package com.smartlife.common.util;

import com.smartlife.common.constant.JwtClaimsConstants;
import com.smartlife.common.model.LoginUser;
import com.smartlife.common.model.TokenPayload;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具：HS256 签名。只负责"签名有效"，不代表会话还活着——会话活性由 Redis 白名单裁定。
 * 不提供重签能力：JWT 有效期 24h 远大于 Redis 的 30min，同一张 token 能用到会话自然结束。
 * javax.crypto.SecretKey 是 JDK 标准库，jakarta 迁移时不能跟着改包名。
 */
@Slf4j
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expireMs;

    public JwtUtil(@Value("${smartlife.jwt.secret}") String secret,
                   @Value("${smartlife.jwt.expire-minutes:1440}") long expireMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMs = expireMinutes * 60 * 1000;
    }

    /**
     * 签发 token。
     * 写入的 userId/role/nickname 不参与鉴权决策（拦截器以 Redis 会话为准），只为自解释。
     */
    public String createToken(LoginUser user, String jti, long ver) {
        Date now = new Date();
        return Jwts.builder()
                .setId(jti)
                .setSubject(String.valueOf(user.getId()))
                .claim(JwtClaimsConstants.CLAIM_USER_ID, user.getId())
                .claim(JwtClaimsConstants.CLAIM_ROLE, user.getRole())
                .claim(JwtClaimsConstants.CLAIM_NICKNAME, user.getNickname())
                .claim(JwtClaimsConstants.CLAIM_VER, ver)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expireMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /** 验签并解析。签名错误、格式非法、已过期一律返回 null，由调用方按 401 处理。 */
    public TokenPayload parse(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            LoginUser user = new LoginUser(
                    claims.get(JwtClaimsConstants.CLAIM_USER_ID, Number.class) == null
                            ? Long.valueOf(claims.getSubject())
                            : claims.get(JwtClaimsConstants.CLAIM_USER_ID, Number.class).longValue(),
                    claims.get(JwtClaimsConstants.CLAIM_ROLE, Integer.class),
                    claims.get(JwtClaimsConstants.CLAIM_NICKNAME, String.class));

            Number ver = claims.get(JwtClaimsConstants.CLAIM_VER, Number.class);
            return new TokenPayload(user, claims.getId(), ver == null ? 0L : ver.longValue());
        } catch (Exception e) {
            log.debug("JWT 解析失败：{}", e.getMessage());
            return null;
        }
    }
}
