package com.smartlife.common.constant;

/**
 * JWT 自定义 claim 名。
 * jti 与 exp 是 RFC 7519 标准 claim，不在这里重复定义——
 * 签发走 Jwts.builder().setId(jti)，解析走 claims.getId()。
 */
public class JwtClaimsConstants {

    private JwtClaimsConstants() {
    }

    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_NICKNAME = "nickname";

    /** 签发时的会话版本号，与 login:ver:{userId} 比对，不等即视为已踢下线（§5.1.4） */
    public static final String CLAIM_VER = "ver";
}
