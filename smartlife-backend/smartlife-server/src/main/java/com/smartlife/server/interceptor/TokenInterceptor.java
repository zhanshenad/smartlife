package com.smartlife.server.interceptor;

import com.smartlife.common.constant.RedisConstants;
import com.smartlife.common.context.BaseContext;
import com.smartlife.common.model.LoginUser;
import com.smartlife.common.model.TokenPayload;
import com.smartlife.common.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.Map;

/**
 * 第一级拦截器：校验 + 填充上下文 + 会话续期，永不拦截请求。
 * "要不要拦"交给 AuthInterceptor。之所以覆盖全部路径，是为了让所有请求都参与会话续期
 * ——只拦部分路径会导致有些请求永远不刷新 TTL。
 * 身份取自 Redis 会话，不取 JWT：JWT 只证明"签名有效"，会话白名单才是"这人是谁、还在不在"。
 * 若采信 JWT 里的 role，secret 泄漏后被伪造的 token 就能提权。
 */
@Slf4j
@Component
public class TokenInterceptor implements HandlerInterceptor {

    /** 校验失败原因，写入 request attribute 供第二级拦截器返回精确提示 */
    public static final String ATTR_FAIL_REASON = "SMARTLIFE_AUTH_FAIL_REASON";

    private static final String AUTH_HEADER = "authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    /** 提示语集中在此，避免同一个原因在多个分支写出不同文案 */
    private static final String MSG_EXPIRED = "登录已过期，请重新登录";
    private static final String MSG_KICKED = "账号已在其他设备登出";

    /** 按阈值续期：只有剩余 TTL 低于阈值才写。脚本正文见 resources/lua/session-renew.lua */
    private static final DefaultRedisScript<Long> RENEW_SCRIPT;

    static {
        RENEW_SCRIPT = new DefaultRedisScript<>();
        RENEW_SCRIPT.setLocation(new ClassPathResource("lua/session-renew.lua"));
        RENEW_SCRIPT.setResultType(Long.class);
    }

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;

    public TokenInterceptor(JwtUtil jwtUtil, StringRedisTemplate stringRedisTemplate) {
        this.jwtUtil = jwtUtil;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = resolveToken(request);
        if (token == null) {
            // 没带 token 不是错误：可能是白名单接口（登录、文档），交由第二级判定
            return true;
        }

        // ① 验签 + 解析。此处只采信 jti 与 ver，userId/role 一律不用（见类注释）
        TokenPayload payload = jwtUtil.parse(token);
        if (payload == null || payload.jti() == null) {
            request.setAttribute(ATTR_FAIL_REASON, MSG_EXPIRED);
            return true;
        }

        // ② 读会话白名单 —— 身份的权威来源
        String tokenKey = RedisConstants.LOGIN_TOKEN_KEY + payload.jti();
        Map<Object, Object> session = stringRedisTemplate.opsForHash().entries(tokenKey);
        if (session.isEmpty()) {
            // 键不存在 = 被踢单端，或 30 分钟没活动自然过期
            request.setAttribute(ATTR_FAIL_REASON, MSG_EXPIRED);
            return true;
        }

        Long userId = toLong(session.get(RedisConstants.SESSION_FIELD_USER_ID));
        if (userId == null) {
            // 脏数据（比如手工 HSET 漏了字段）。当作会话失效处理，不抛异常
            log.warn("会话数据缺少 userId 字段，tokenKey={}", tokenKey);
            request.setAttribute(ATTR_FAIL_REASON, MSG_EXPIRED);
            return true;
        }

        String verKey = RedisConstants.LOGIN_VER_KEY + userId;
        String userKey = RedisConstants.LOGIN_USER_KEY + userId;

        // ③ 版本号比对：ver 不存在视为 0（该用户从没被踢过）
        String verValue = stringRedisTemplate.opsForValue().get(verKey);
        long currentVer = (verValue == null) ? 0L : parseLongOrZero(verValue);
        if (payload.ver() != currentVer) {
            request.setAttribute(ATTR_FAIL_REASON, MSG_KICKED);
            return true;
        }

        // ④ 全部通过。身份字段全部取自 Redis，不取 JWT
        LoginUser user = new LoginUser(
                userId,
                toInt(session.get(RedisConstants.SESSION_FIELD_ROLE)),
                (String) session.get(RedisConstants.SESSION_FIELD_NICKNAME));
        user.setJti(payload.jti());

        BaseContext.set(user);
        renewIfNeeded(tokenKey, userKey);
        return true;
    }

    /**
     * 必须清理：Tomcat 复用线程，残留的上下文会让下一个请求读到上一个用户的身份，
     * 这是真实的越权漏洞。放在这里而不是 AuthInterceptor，是因为 set 也发生在本类。
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        BaseContext.remove();
    }

    private void renewIfNeeded(String tokenKey, String userKey) {
        try {
            stringRedisTemplate.execute(RENEW_SCRIPT,
                    List.of(tokenKey, userKey),
                    String.valueOf(RedisConstants.LOGIN_REFRESH_THRESHOLD_MINUTES * 60),
                    String.valueOf(RedisConstants.LOGIN_TOKEN_TTL_MINUTES * 60));
        } catch (Exception e) {
            // 续期失败不应让正常请求失败：本次照常放行，下一个请求会再试
            log.warn("会话续期失败，tokenKey={}，原因：{}", tokenKey, e.getMessage());
        }
    }

    /**
     * 从 authorization 头取 token。
     * 兼容 Bearer xxx 与裸 token 两种写法——Knife4j 的调试面板默认只填 token 本身。
     */
    public static String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(AUTH_HEADER);
        if (header == null || header.isBlank()) {
            return null;
        }
        return header.startsWith(BEARER_PREFIX) ? header.substring(BEARER_PREFIX.length()).trim() : header.trim();
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer toInt(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static long parseLongOrZero(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
