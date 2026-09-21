package com.smartlife.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartlife.common.constant.RedisConstants;
import com.smartlife.common.constant.RoleConstants;
import com.smartlife.common.exception.BusinessException;
import com.smartlife.common.model.TokenPayload;
import com.smartlife.common.util.JwtUtil;
import com.smartlife.pojo.dto.LoginDTO;
import com.smartlife.pojo.entity.User;
import com.smartlife.pojo.vo.LoginVO;
import com.smartlife.server.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 登录与验证码的闭环测试。依赖本机 MySQL(smartlife) 与 Redis(db2)。
 * 手机号用 199 号段的假号码，测试后清理用户与 Redis 键。
 */
@SpringBootTest
@DisplayName("登录与验证码")
class UserServiceImplTest {

    private static final String PHONE = "19999990001";

    @Autowired
    private IUserService userService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private JwtUtil jwtUtil;

    @AfterEach
    void cleanUp() {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, PHONE));
        if (user != null) {
            userMapper.deleteById(user.getId());
            redis.delete(RedisConstants.LOGIN_USER_KEY + user.getId());
            redis.delete(RedisConstants.LOGIN_VER_KEY + user.getId());
        }
        redis.delete(RedisConstants.LOGIN_CODE_KEY + PHONE);
    }

    @Test
    @DisplayName("新手机号登录：自动注册 + 返回 token + 会话白名单落 Redis")
    void loginRegistersNewUser() {
        LoginVO vo = loginAfterSendCode();

        assertNotNull(vo.getToken());
        assertEquals(RoleConstants.USER, vo.getRole());
        assertEquals("/user", vo.getHomePath());

        TokenPayload payload = jwtUtil.parse(vo.getToken());
        assertNotNull(payload);
        Map<Object, Object> session = redis.opsForHash()
                .entries(RedisConstants.LOGIN_TOKEN_KEY + payload.jti());
        assertFalse(session.isEmpty(), "会话白名单必须写入 Redis");
        assertEquals(String.valueOf(payload.user().getId()),
                session.get(RedisConstants.SESSION_FIELD_USER_ID));
    }

    @Test
    @DisplayName("验证码用后即删：同一个码不能登录第二次")
    void codeIsSingleUse() {
        userService.sendCode(PHONE);
        String code = redis.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + PHONE);
        LoginDTO dto = buildDto(code);

        LoginVO first = userService.login(dto);
        assertNotNull(first.getToken());

        assertThrows(BusinessException.class, () -> userService.login(dto),
                "同一个验证码二次登录必须被拒绝");
    }

    @Test
    @DisplayName("错误验证码：拒绝登录")
    void wrongCodeRejected() {
        userService.sendCode(PHONE);
        String realCode = redis.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + PHONE);
        String wrongCode = "000000".equals(realCode) ? "111111" : "000000";

        assertThrows(BusinessException.class, () -> userService.login(buildDto(wrongCode)));
    }

    @Test
    @DisplayName("禁用账号：拒绝登录")
    void disabledUserRejected() {
        loginAfterSendCode();
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, PHONE));
        assertNotNull(user);
        user.setStatus(0);
        userMapper.updateById(user);

        assertThrows(BusinessException.class, this::loginAfterSendCode);
    }

    @Test
    @DisplayName("自动注册的昵称有默认前缀，状态为启用")
    void newUserDefaults() {
        loginAfterSendCode();
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, PHONE));

        assertNotNull(user);
        assertTrue(user.getNickName().startsWith("用户"));
        assertEquals(RoleConstants.USER, user.getRole());
        assertEquals(1, user.getStatus());
    }

    private LoginVO loginAfterSendCode() {
        userService.sendCode(PHONE);
        String code = redis.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + PHONE);
        return userService.login(buildDto(code));
    }

    private LoginDTO buildDto(String code) {
        LoginDTO dto = new LoginDTO();
        dto.setPhone(PHONE);
        dto.setCode(code);
        return dto;
    }
}
