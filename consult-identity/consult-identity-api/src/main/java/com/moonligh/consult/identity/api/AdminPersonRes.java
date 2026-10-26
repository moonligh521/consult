package com.moonligh.consult.identity.api;

/**
 * 管理端就诊人一行。带所属账号。
 */
public record AdminPersonRes(
        String id,
        String accountId,
        String mobile,
        String name,
        int gender,
        int age,
        String relation,
        boolean isDefault) {
}
