package com.smartlife.common.model;

/**
 * JWT 解析结果。
 * jti 与 ver 必须单独带出来——拦截器要靠它俩查 Redis 白名单、比对会话版本。
 */
public record TokenPayload(LoginUser user, String jti, long ver) {
}
