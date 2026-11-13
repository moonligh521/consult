package com.moonligh.consult.identity.domain;

/**
 * 登录账号。
 */
public record AccountRecord(String id, String mobile, int status) {

    public boolean enabled() {
        return status == 1;
    }
}
