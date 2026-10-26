package com.moonligh.consult.identity.api;

/**
 * 登录成功后的令牌。后续请求放在 Authorization: Bearer。
 */
public record LoginRes(String token, String accountId, String mobile, long expiresIn) {
}
