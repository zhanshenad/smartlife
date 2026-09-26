package com.smartlife.server.websocket;

import com.smartlife.common.constant.RedisConstants;
import com.smartlife.common.constant.RoleConstants;
import com.smartlife.common.model.TokenPayload;
import com.smartlife.common.util.JwtUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * ws 握手鉴权。浏览器 WebSocket 不能自定义 Header，token 走 query 参数。
 * 校验链与 TokenInterceptor 同源：验签 → 会话存在 → 身份匹配（身份取自 Redis 会话，不取 JWT）。
 * 只在握手时校验一次；连着期间被踢不会断开（消息仅含订单号，低危，记录在案）。
 */
@Component
public class SessionResolver {

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redis;

    public SessionResolver(JwtUtil jwtUtil, StringRedisTemplate redis) {
        this.jwtUtil = jwtUtil;
        this.redis = redis;
    }

    /** token 能否订阅 sid（= userId）的提醒。管理员放行任意商家（巡检场景） */
    public boolean authorize(String token, String sid) {
        if (token == null || token.isBlank() || sid == null) {
            return false;
        }
        TokenPayload payload = jwtUtil.parse(token);
        if (payload == null || payload.jti() == null) {
            return false;
        }
        Map<Object, Object> session = redis.opsForHash()
                .entries(RedisConstants.LOGIN_TOKEN_KEY + payload.jti());
        if (session.isEmpty()) {
            return false;
        }
        Long userId = toLong(session.get(RedisConstants.SESSION_FIELD_USER_ID));
        if (userId == null) {
            return false;
        }
        Integer role = toInt(session.get(RedisConstants.SESSION_FIELD_ROLE));
        if (role != null && role == RoleConstants.ADMIN) {
            return true;
        }
        return sid.equals(userId.toString());
    }

    private static Long toLong(Object value) {
        try {
            return value == null ? null : Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer toInt(Object value) {
        try {
            return value == null ? null : Integer.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
