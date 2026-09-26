package com.smartlife.server.service;

import com.smartlife.common.constant.RedisConstants;
import com.smartlife.common.model.LoginUser;
import com.smartlife.common.util.JwtUtil;
import com.smartlife.server.websocket.SessionResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ws 握手鉴权测试：本人放行、冒充拒绝、伪造 JWT 拒绝、管理员放行 */
@SpringBootTest
@DisplayName("ws 连接鉴权")
class SessionResolverTest {

    private static final Long MERCHANT_ID = 992L;
    private static final Long OTHER_ID = 991L;
    private static final String JTI = "ws-auth-test-jti";

    @Autowired
    private SessionResolver sessionResolver;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private StringRedisTemplate redis;

    @AfterEach
    void cleanUp() {
        redis.delete(RedisConstants.LOGIN_TOKEN_KEY + JTI);
        redis.delete(RedisConstants.LOGIN_USER_KEY + MERCHANT_ID);
    }

    private void givenSession(Long userId, int role) {
        String tokenKey = RedisConstants.LOGIN_TOKEN_KEY + JTI;
        redis.opsForHash().put(tokenKey, RedisConstants.SESSION_FIELD_USER_ID, String.valueOf(userId));
        redis.opsForHash().put(tokenKey, RedisConstants.SESSION_FIELD_ROLE, String.valueOf(role));
        redis.opsForHash().put(tokenKey, RedisConstants.SESSION_FIELD_NICKNAME, "测试");
        redis.expire(tokenKey, Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("本人 token 订阅自己的 sid：放行")
    void allowsOwner() {
        givenSession(MERCHANT_ID, 2);
        String token = jwtUtil.createToken(new LoginUser(MERCHANT_ID, 2, "商家"), JTI, 0L);

        assertTrue(sessionResolver.authorize(token, MERCHANT_ID.toString()));
    }

    @Test
    @DisplayName("拿自己的 token 订阅别人的 sid：拒绝")
    void rejectsImpersonation() {
        givenSession(MERCHANT_ID, 2);
        String token = jwtUtil.createToken(new LoginUser(MERCHANT_ID, 2, "商家"), JTI, 0L);

        assertFalse(sessionResolver.authorize(token, OTHER_ID.toString()));
    }

    @Test
    @DisplayName("无 token / 伪造 JWT / 会话已过期：全部拒绝")
    void rejectsInvalidTokens() {
        givenSession(MERCHANT_ID, 2);
        String forged = "eyJhbGciOiJIUzI1NiJ9.e30.forged-signature";

        assertFalse(sessionResolver.authorize(null, MERCHANT_ID.toString()));
        assertFalse(sessionResolver.authorize(forged, MERCHANT_ID.toString()));

        // 合法签名但 Redis 里没有这个会话（被踢/过期）
        String ghost = jwtUtil.createToken(new LoginUser(MERCHANT_ID, 2, "商家"), "ghost-jti", 0L);
        assertFalse(sessionResolver.authorize(ghost, MERCHANT_ID.toString()));
    }

    @Test
    @DisplayName("管理员可订阅任意商家（订单巡检场景）")
    void allowsAdminAnywhere() {
        givenSession(990L, 3);
        String token = jwtUtil.createToken(new LoginUser(990L, 3, "管理员"), JTI, 0L);

        assertTrue(sessionResolver.authorize(token, MERCHANT_ID.toString()));
    }
}
