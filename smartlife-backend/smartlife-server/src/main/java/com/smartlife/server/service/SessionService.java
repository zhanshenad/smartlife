package com.smartlife.server.service;

import com.smartlife.common.constant.RedisConstants;
import com.smartlife.common.context.BaseContext;
import com.smartlife.common.model.LoginUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * 会话治理：踢人下线与在线会话查询，设计见《重构计划》§5.1.5。
 */
@Slf4j
@Service
public class SessionService {

    private final StringRedisTemplate redis;

    public SessionService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 踢全端：版本号加一，该用户所有旧 token 立即失效。ver 键永不设 TTL */
    public void kickAll(Long userId) {
        redis.opsForValue().increment(RedisConstants.LOGIN_VER_KEY + userId);
        // 清反向索引只为在线列表准确，不清也不影响安全性
        redis.delete(RedisConstants.LOGIN_USER_KEY + userId);
    }

    /** 踢单端：删掉指定会话，其余端不受影响 */
    public void kickOne(String jti) {
        String tokenKey = RedisConstants.LOGIN_TOKEN_KEY + jti;
        // 先读出 userId 才能顺带清理反向索引
        Object userId = redis.opsForHash().get(tokenKey, RedisConstants.SESSION_FIELD_USER_ID);
        redis.delete(tokenKey);
        if (userId != null) {
            redis.opsForSet().remove(RedisConstants.LOGIN_USER_KEY + userId, jti);
        }
    }

    /** 登出：只踢当前这一台设备 */
    public void logoutCurrent() {
        LoginUser user = BaseContext.require();
        if (user.getJti() != null) {
            kickOne(user.getJti());
        }
    }

    /** 某用户的在线会话列表。反向索引里的 jti 可能已过期，逐条过滤 */
    public List<String> listOnlineJti(Long userId) {
        Set<String> jtis = redis.opsForSet().members(RedisConstants.LOGIN_USER_KEY + userId);
        if (jtis == null) {
            return List.of();
        }
        return jtis.stream()
                .filter(jti -> redis.hasKey(RedisConstants.LOGIN_TOKEN_KEY + jti))
                .toList();
    }
}
