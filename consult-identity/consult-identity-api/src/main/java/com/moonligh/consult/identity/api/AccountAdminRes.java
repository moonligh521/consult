package com.moonligh.consult.identity.api;

/**
 * 管理端看到的患者账号。
 */
public record AccountAdminRes(String id, String mobile, int status, int personCount, String createdAt) {
}
