package com.smartlife.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartlife.common.constant.RedisConstants;
import com.smartlife.common.context.BaseContext;
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

    // ==================== 密码通道 ====================

    @Test
    @DisplayName("验证码登录返回 passwordSet=false：新用户未设密码，前端据此引导")
    void newLoginReportsPasswordUnset() {
        LoginVO vo = loginAfterSendCode();
        assertEquals(Boolean.FALSE, vo.getPasswordSet());
    }

    @Test
    @DisplayName("设密 → 密码登录成功；未设密码/错密/不存在的账号全部拒绝")
    void passwordLoginFlow() {
        LoginVO vo = loginAfterSendCode();
        Long userId = jwtUtil.parse(vo.getToken()).user().getId();
        BaseContext.set(new com.smartlife.common.model.LoginUser(userId, 1, "测试"));

        // 未设密码：密码登录拒绝
        LoginDTO dto = new LoginDTO();
        dto.setPhone(PHONE);
        dto.setPassword("test123456");
        BusinessException e1 = assertThrows(BusinessException.class, () -> userService.login(dto));
        assertTrue(e1.getMessage().contains("尚未设置密码"));

        // 首次设置：免旧密
        com.smartlife.pojo.dto.PasswordDTO set = new com.smartlife.pojo.dto.PasswordDTO();
        set.setNewPassword("test123456");
        userService.setPassword(set);

        LoginVO byPassword = userService.login(dto);
        assertNotNull(byPassword.getToken());
        assertEquals(Boolean.TRUE, byPassword.getPasswordSet());

        // 错误密码拒绝
        dto.setPassword("wrong123456");
        BusinessException e2 = assertThrows(BusinessException.class, () -> userService.login(dto));
        assertTrue(e2.getMessage().contains("密码错误"));

        // 不存在的账号拒绝（密码通道不注册）
        dto.setPhone("19999990999");
        BusinessException e3 = assertThrows(BusinessException.class, () -> userService.login(dto));
        assertTrue(e3.getMessage().contains("账号不存在"));
    }

    @Test
    @DisplayName("修改密码：验旧密 + 踢全端；首次设置不踢")
    void changePasswordKicksAll() {
        LoginVO vo = loginAfterSendCode();
        Long userId = jwtUtil.parse(vo.getToken()).user().getId();
        BaseContext.set(new com.smartlife.common.model.LoginUser(userId, 1, "测试"));

        com.smartlife.pojo.dto.PasswordDTO first = new com.smartlife.pojo.dto.PasswordDTO();
        first.setNewPassword("first123456");
        userService.setPassword(first);
        assertFalse(Boolean.TRUE.equals(redis.hasKey(RedisConstants.LOGIN_VER_KEY + userId)),
                "首次设置密码不应踢会话");

        // 旧密码错误：拒绝修改
        com.smartlife.pojo.dto.PasswordDTO wrongOld = new com.smartlife.pojo.dto.PasswordDTO();
        wrongOld.setOldPassword("wrongold1");
        wrongOld.setNewPassword("second123456");
        assertThrows(BusinessException.class, () -> userService.setPassword(wrongOld));

        // 旧密码正确：改密成功并踢全端（ver +1）
        wrongOld.setOldPassword("first123456");
        userService.setPassword(wrongOld);
        assertEquals("1", redis.opsForValue().get(RedisConstants.LOGIN_VER_KEY + userId),
                "修改密码应踢全端会话");
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
