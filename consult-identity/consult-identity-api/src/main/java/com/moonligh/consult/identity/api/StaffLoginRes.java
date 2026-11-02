package com.moonligh.consult.identity.api;

/**
 * 管理端登录成功。
 */
public record StaffLoginRes(String token, String staffId, String username, String name, long expiresIn) {
}
