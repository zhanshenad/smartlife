package com.smartlife.server.service;

import com.smartlife.common.constant.RedisConstants;
import com.smartlife.common.context.BaseContext;
import com.smartlife.common.model.LoginUser;
import com.smartlife.common.util.JwtUtil;
import com.smartlife.pojo.vo.OnlineSessionVO;
import com.smartlife.server.interceptor.TokenInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 会话治理测试：踢全端、踢单端、在线列表。
 * 最后一条用例跑通 P1 的验收闭环：登录拿 token → 可访问 → 踢人 → 立即失效。
 */
@SpringBootTest
@DisplayName("会话治理：踢人下线")
class SessionServiceTest {

    private static final Long USER_ID = 998L;
    private static final String JTI = "session-test-jti";

    @Autowired
    private SessionService sessionService;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private TokenInterceptor tokenInterceptor;

    @AfterEach
    void cleanUp() {
        BaseContext.remove();
        redis.delete(RedisConstants.LOGIN_TOKEN_KEY + JTI);
        redis.delete(RedisConstants.LOGIN_USER_KEY + USER_ID);
        redis.delete(RedisConstants.LOGIN_VER_KEY + USER_ID);
    }

    @Test
    @DisplayName("踢全端：版本号加一，反向索引被清")
    void kickAllBumpsVersion() {
        givenSession();

        sessionService.kickAll(USER_ID);

        assertEquals("1", redis.opsForValue().get(RedisConstants.LOGIN_VER_KEY + USER_ID));
        assertFalse(redis.hasKey(RedisConstants.LOGIN_USER_KEY + USER_ID));
    }

    @Test
    @DisplayName("踢单端：会话删除，反向索引同步移除")
    void kickOneRemovesSession() {
        givenSession();

        sessionService.kickOne(JTI);

        assertFalse(redis.hasKey(RedisConstants.LOGIN_TOKEN_KEY + JTI));
        assertTrue(sessionService.listOnlineJti(USER_ID).isEmpty());
    }

    @Test
    @DisplayName("在线列表：反向索引里已过期的 jti 不会出现")
    void listOnlineFiltersExpired() {
        givenSession();
        // 塞一个没有真实会话的 jti，模拟"Set 成员还在但 token 已过期"
        redis.opsForSet().add(RedisConstants.LOGIN_USER_KEY + USER_ID, "ghost-jti");

        List<String> online = sessionService.listOnlineJti(USER_ID);

        assertEquals(List.of(JTI), online);
    }

    @Test
    @DisplayName("全站在线列表：直扫会话键，带身份与剩余 TTL")
    void listOnlineAllScansTokenKeys() {
        givenSession();

        List<OnlineSessionVO> all = sessionService.listOnlineAll();

        // 库里可能还有其他会话，只断言自己这条在场且字段齐全
        OnlineSessionVO mine = all.stream()
                .filter(vo -> JTI.equals(vo.getJti()))
                .findFirst().orElseThrow();
        assertEquals(USER_ID, mine.getUserId());
        assertEquals(1, mine.getRole());
        assertEquals("测试", mine.getNickname());
        assertTrue(mine.getTtlSeconds() != null && mine.getTtlSeconds() > 0);
    }

    @Test
    @DisplayName("★ P1 验收闭环：登录有效 → 踢全端 → 同一个 token 立即失效")
    void kickAllInvalidatesTokenImmediately() {
        givenSession();
        String token = jwtUtil.createToken(new LoginUser(USER_ID, 1, "测试"), JTI, 0L);

        invoke(token);
        assertNotNull(BaseContext.get(), "踢之前 token 应当有效");

        sessionService.kickAll(USER_ID);

        invoke(token);
        assertNull(BaseContext.get(), "踢之后同一个 token 必须立即失效");
        assertEquals("账号已在其他设备登出",
                lastRequest.getAttribute(TokenInterceptor.ATTR_FAIL_REASON));
    }

    // ==================== 辅助 ====================

    private MockHttpServletRequest lastRequest;

    private void givenSession() {
        String tokenKey = RedisConstants.LOGIN_TOKEN_KEY + JTI;
        redis.opsForHash().put(tokenKey, RedisConstants.SESSION_FIELD_USER_ID, String.valueOf(USER_ID));
        redis.opsForHash().put(tokenKey, RedisConstants.SESSION_FIELD_ROLE, "1");
        redis.opsForHash().put(tokenKey, RedisConstants.SESSION_FIELD_NICKNAME, "测试");
        redis.expire(tokenKey, Duration.ofMinutes(30));
        redis.opsForSet().add(RedisConstants.LOGIN_USER_KEY + USER_ID, JTI);
    }

    private void invoke(String token) {
        // 直调 preHandle 不经过 MVC 生命周期，afterCompletion 不会执行，
        // 必须手动清残留，否则上一个 invoke 设的身份会漏到这次
        BaseContext.remove();
        lastRequest = new MockHttpServletRequest();
        lastRequest.addHeader("authorization", token);
        tokenInterceptor.preHandle(lastRequest, new MockHttpServletResponse(), new Object());
    }
}
