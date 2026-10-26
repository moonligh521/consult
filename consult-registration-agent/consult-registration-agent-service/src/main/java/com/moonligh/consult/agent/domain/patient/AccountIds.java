package com.moonligh.consult.agent.domain.patient;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 当前登录账号。演示环境没有登录态，缺头时用 demo。
 */
public final class AccountIds {

    public static final String DEFAULT = "demo";
    public static final String HEADER = "X-Account-Id";

    private AccountIds() {
    }

    /** 请求头里的账号。空或没传就落到 demo。 */
    public static String of(HttpServletRequest request) {
        if (request == null) {
            return DEFAULT;
        }
        String id = request.getHeader(HEADER);
        if (id == null || id.isBlank()) {
            return DEFAULT;
        }
        return id.trim();
    }

    /** 服务层缺账号时同样落到 demo。 */
    public static String orDefault(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return DEFAULT;
        }
        return accountId.trim();
    }
}
