package com.moonligh.consult.security;

/**
 * 已登录账号。网关校验通过后写入请求头，咨询和就诊人只认这个。
 */
public record AuthPrincipal(String accountId, String mobile, String role) {

    public static final String PATIENT = "patient";
    public static final String STAFF = "staff";

    public AuthPrincipal(String accountId, String mobile) {
        this(accountId, mobile, PATIENT);
    }

    public boolean staff() {
        return STAFF.equals(role);
    }
}
