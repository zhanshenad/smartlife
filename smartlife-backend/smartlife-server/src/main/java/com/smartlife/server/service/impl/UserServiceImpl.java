package com.smartlife.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlife.common.constant.RedisConstants;
import com.smartlife.common.constant.RoleConstants;
import com.smartlife.common.constant.StatusConstants;
import com.smartlife.common.exception.BusinessException;
import com.smartlife.common.model.LoginUser;
import com.smartlife.common.util.JwtUtil;
import com.smartlife.pojo.dto.LoginDTO;
import com.smartlife.pojo.entity.User;
import com.smartlife.pojo.vo.LoginVO;
import com.smartlife.server.mapper.UserMapper;
import com.smartlife.server.service.IUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 登录实现：验证码校验、查建用户、签发 JWT、写会话白名单，流程见《重构计划》§5.1.3。
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

    public UserServiceImpl(StringRedisTemplate redis, JwtUtil jwtUtil) {
        this.redis = redis;
        this.jwtUtil = jwtUtil;
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
        // 校验验证码，通过后立即删除：同一个码不能登录第二次
        String codeKey = RedisConstants.LOGIN_CODE_KEY + dto.getPhone();
        String cacheCode = redis.opsForValue().get(codeKey);
        if (cacheCode == null || !cacheCode.equals(dto.getCode())) {
            throw new BusinessException("验证码错误或已过期");
        }
        redis.delete(codeKey);

        // 查用户，不存在则自动注册
        User user = query().eq("phone", dto.getPhone()).one();
        if (user == null) {
            user = createUserWithPhone(dto.getPhone());
        }
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

        return new LoginVO(token, user.getRole(), homePath(user.getRole()));
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
