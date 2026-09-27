package com.smartlife.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlife.common.constant.RedisConstants;
import com.smartlife.common.constant.RoleConstants;
import com.smartlife.common.constant.StatusConstants;
import com.smartlife.common.context.BaseContext;
import com.smartlife.common.exception.BusinessException;
import com.smartlife.common.model.LoginUser;
import com.smartlife.common.util.JwtUtil;
import com.smartlife.pojo.dto.LoginDTO;
import com.smartlife.pojo.dto.PasswordDTO;
import com.smartlife.pojo.entity.User;
import com.smartlife.pojo.vo.LoginVO;
import com.smartlife.server.mapper.UserMapper;
import com.smartlife.server.service.IUserService;
import com.smartlife.server.service.SessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 登录实现：验证码/密码双通道 + 会话签发，流程见《重构计划》§5.1.3。
 * 密码 BCrypt 存储（慢哈希 + 盐内嵌密文），设密/改密见 setPassword。
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private static final String NICKNAME_PREFIX = "用户";

    /** 会话写入脚本。正文见 resources/lua/session-create.lua */
    private static final DefaultRedisScript<Long> SESSION_CREATE_SCRIPT;

    static {
        SESSION_CREATE_SCRIPT = new DefaultRedisScript<>();
        SESSION_CREATE_SCRIPT.setLocation(new ClassPathResource("lua/session-create.lua"));
        SESSION_CREATE_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redis;
    private final JwtUtil jwtUtil;
    private final SessionService sessionService;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserServiceImpl(StringRedisTemplate redis, JwtUtil jwtUtil, SessionService sessionService) {
        this.redis = redis;
        this.jwtUtil = jwtUtil;
        this.sessionService = sessionService;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @Override
    public void sendCode(String phone) {
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
        redis.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code,
                RedisConstants.LOGIN_CODE_TTL_MINUTES, TimeUnit.MINUTES);
        // 没接真实短信通道，开发期从日志取码
        log.info("手机号 {} 的登录验证码：{}", phone, code);
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        if (dto.getCode() != null && !dto.getCode().isBlank()) {
            return loginByCode(dto);
        }
        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            return loginByPassword(dto);
        }
        throw new BusinessException("验证码与密码至少填一项");
    }

    /** 验证码通道：校验通过即查/建用户（登录即注册），新用户无密码由前端引导设置 */
    private LoginVO loginByCode(LoginDTO dto) {
        // 校验验证码，通过后立即删除：同一个码不能登录第二次
        String codeKey = RedisConstants.LOGIN_CODE_KEY + dto.getPhone();
        String cacheCode = redis.opsForValue().get(codeKey);
        if (cacheCode == null || !cacheCode.equals(dto.getCode())) {
            throw new BusinessException("验证码错误或已过期");
        }
        redis.delete(codeKey);

        User user = query().eq("phone", dto.getPhone()).one();
        if (user == null) {
            user = createUserWithPhone(dto.getPhone());
        }
        return issueSession(user);
    }

    /** 密码通道：只登录不注册（未注册/未设密码引导走验证码通道） */
    private LoginVO loginByPassword(LoginDTO dto) {
        User user = query().eq("phone", dto.getPhone()).one();
        if (user == null) {
            throw new BusinessException("账号不存在，请先通过验证码登录完成注册");
        }
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            throw new BusinessException("该账号尚未设置密码，请先通过验证码登录");
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException("密码错误");
        }
        return issueSession(user);
    }

    /** 公共签发：禁用检查 + jti/ver 会话写入，两条登录通道的同一落点 */
    private LoginVO issueSession(User user) {
        if (user.getStatus() != null && user.getStatus() == StatusConstants.Common.DISABLED) {
            throw new BusinessException("账号已被禁用，请联系客服");
        }

        // 版本号不存在视为 0（该用户从未被踢过）
        String verValue = redis.opsForValue().get(RedisConstants.LOGIN_VER_KEY + user.getId());
        long ver = verValue == null ? 0L : Long.parseLong(verValue);

        String jti = UUID.randomUUID().toString();
        LoginUser loginUser = new LoginUser(user.getId(), user.getRole(), user.getNickName());
        String token = jwtUtil.createToken(loginUser, jti, ver);

        // 会话白名单 + 反向索引，一个脚本原子写入，避免留下无 TTL 的永久会话键
        String tokenKey = RedisConstants.LOGIN_TOKEN_KEY + jti;
        String userKey = RedisConstants.LOGIN_USER_KEY + user.getId();
        String nickname = user.getNickName() == null ? "" : user.getNickName();
        redis.execute(SESSION_CREATE_SCRIPT,
                List.of(tokenKey, userKey),
                String.valueOf(RedisConstants.LOGIN_TOKEN_TTL_MINUTES * 60),
                jti,
                String.valueOf(user.getId()),
                String.valueOf(user.getRole()),
                nickname);

        boolean passwordSet = user.getPassword() != null && !user.getPassword().isBlank();
        return new LoginVO(token, user.getRole(), homePath(user.getRole()), passwordSet);
    }

    @Override
    public void setPassword(PasswordDTO dto) {
        User user = getById(BaseContext.require().getId());
        boolean hasPassword = user.getPassword() != null && !user.getPassword().isBlank();
        if (hasPassword) {
            // 已有密码：必须验旧密码（苍穹 editPassword 同款护栏）
            if (dto.getOldPassword() == null
                    || !passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
                throw new BusinessException("旧密码错误");
            }
        }
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        updateById(user);
        if (hasPassword) {
            // 修改密码踢全端强制重登（安全惯例）；首次设置不踢，当前会话保留
            sessionService.kickAll(user.getId());
        }
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(NICKNAME_PREFIX + ThreadLocalRandom.current().nextInt(10000000, 100000000));
        user.setRole(RoleConstants.USER);
        user.setStatus(StatusConstants.Common.ENABLED);
        save(user);
        return user;
    }

    private String homePath(Integer role) {
        if (role == null) {
            return "/user";
        }
        return switch (role) {
            case RoleConstants.MERCHANT -> "/merchant";
            case RoleConstants.ADMIN -> "/admin";
            default -> "/user";
        };
    }
}
