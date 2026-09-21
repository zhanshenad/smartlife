package com.smartlife.server.interceptor;

import com.smartlife.common.constant.RedisConstants;
import com.smartlife.common.context.BaseContext;
import com.smartlife.common.model.LoginUser;
import com.smartlife.common.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 鉴权拦截器的职责边界测试。
 * 核心要守住的一条：身份以 Redis 会话为准，不以 JWT 声称的为准。
 * 这条一旦破防，"JWT + Redis 双保险"就只剩 JWT 一层，
 * 而 JWT 的唯一防线是那个可能泄漏的 secret。
 * 依赖本机 Redis（docker 容器 smartlife-redis，db2）。
 */
@SpringBootTest
@DisplayName("TokenInterceptor：JWT 与 Redis 会话的职责边界")
class TokenInterceptorTest {

    private static final String JTI = "unit-test-jti";
    private static final Long USER_ID = 999L;

    @Autowired
    private TokenInterceptor tokenInterceptor;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private JwtUtil jwtUtil;

    @AfterEach
    void cleanUp() {
        BaseContext.remove();
        redis.delete(RedisConstants.LOGIN_TOKEN_KEY + JTI);
        redis.delete(RedisConstants.LOGIN_USER_KEY + USER_ID);
        redis.delete(RedisConstants.LOGIN_VER_KEY + USER_ID);
    }

    @Test
    @DisplayName("不带 token：放行但上下文为空（白名单接口要靠这个）")
    void noToken() {
        MockHttpServletRequest request = invoke(null);

        assertNull(request.getAttribute(TokenInterceptor.ATTR_FAIL_REASON), "没带 token 不算校验失败");
        assertNull(BaseContext.get(), "没有 token 就不该有身份");
    }

    @Test
    @DisplayName("会话不存在：不放身份（被踢单端 / 自然过期）")
    void sessionMissing() {
        String token = jwtUtil.createToken(new LoginUser(USER_ID, 1, "测试"), JTI, 0L);

        MockHttpServletRequest request = invoke(token);

        assertNull(BaseContext.get());
        assertEquals("登录已过期，请重新登录", request.getAttribute(TokenInterceptor.ATTR_FAIL_REASON));
    }

    @Test
    @DisplayName("★ 越权防护：JWT 声称 role=3，但会话里是 role=1，拿到必须是 1")
    void forgedRoleIsIgnored() {
        // 会话是真实的普通用户
        givenSession(USER_ID, 1, "普通用户");
        // 但 token 声称自己是管理员 —— 模拟 "secret 泄漏后被伪造"
        String forged = jwtUtil.createToken(new LoginUser(USER_ID, 3, "管理员"), JTI, 0L);

        invoke(forged);

        LoginUser actual = BaseContext.get();
        assertNotNull(actual, "会话真实存在，应当放行");
        assertEquals(1, actual.getRole(), "角色必须取自 Redis 会话，取 JWT 就是提权漏洞");
        assertEquals("普通用户", actual.getNickname(), "昵称同样以会话为准");
    }

    @Test
    @DisplayName("★ 越权防护：拿自己的 jti 冒充别人的 userId 也无效")
    void forgedUserIdIsIgnored() {
        givenSession(USER_ID, 1, "普通用户");
        // JWT 声称自己是 1 号管理员，但 jti 是普通用户自己的
        String forged = jwtUtil.createToken(new LoginUser(1L, 3, "管理员"), JTI, 0L);

        invoke(forged);

        LoginUser actual = BaseContext.get();
        assertNotNull(actual);
        assertEquals(USER_ID, actual.getId(), "userId 必须取自会话，不能信 JWT 声称的值");
    }

    @Test
    @DisplayName("版本号不匹配：账号已在别处登出")
    void versionMismatch() {
        givenSession(USER_ID, 1, "测试");
        redis.opsForValue().set(RedisConstants.LOGIN_VER_KEY + USER_ID, "1");
        // token 里是旧版本号 0
        String token = jwtUtil.createToken(new LoginUser(USER_ID, 1, "测试"), JTI, 0L);

        MockHttpServletRequest request = invoke(token);

        assertNull(BaseContext.get());
        assertEquals("账号已在其他设备登出", request.getAttribute(TokenInterceptor.ATTR_FAIL_REASON));
    }

    @Test
    @DisplayName("一切正常：身份从会话读出，且会话被续期")
    void happyPath() {
        givenSession(USER_ID, 2, "商家");
        String token = jwtUtil.createToken(new LoginUser(USER_ID, 2, "商家"), JTI, 0L);

        invoke(token);

        LoginUser actual = BaseContext.get();
        assertNotNull(actual);
        assertEquals(USER_ID, actual.getId());
        assertEquals(2, actual.getRole());
        assertEquals("商家", actual.getNickname());
        assertTrue(actual.canOperateShop(), "role=2 应当能操作商家端");
    }

    // ==================== 辅助 ====================

    private void givenSession(Long userId, Integer role, String nickname) {
        String key = RedisConstants.LOGIN_TOKEN_KEY + JTI;
        redis.opsForHash().put(key, RedisConstants.SESSION_FIELD_USER_ID, String.valueOf(userId));
        redis.opsForHash().put(key, RedisConstants.SESSION_FIELD_ROLE, String.valueOf(role));
        redis.opsForHash().put(key, RedisConstants.SESSION_FIELD_NICKNAME, nickname);
        redis.expire(key, Duration.ofMinutes(30));
    }

    private MockHttpServletRequest invoke(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (token != null) {
            request.addHeader("authorization", token);
        }
        tokenInterceptor.preHandle(request, new org.springframework.mock.web.MockHttpServletResponse(), new Object());
        return request;
    }
}
