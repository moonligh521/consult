package com.moonligh.consult.security;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 当前登录账号。没有令牌就不给默认 demo。
 */
public final class AccountIds {

    public static final String HEADER = AuthTokens.ACCOUNT_HEADER;
    static final String ATTR = "consult.auth.principal";

    private AccountIds() {
    }

    public static void bind(HttpServletRequest request, AuthPrincipal principal) {
        request.setAttribute(ATTR, principal);
    }

    public static AuthPrincipal principal(HttpServletRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.LOGIN_REQUIRED);
        }
        Object value = request.getAttribute(ATTR);
        if (value instanceof AuthPrincipal principal) {
            return principal;
        }
        String accountId = request.getHeader(HEADER);
        if (accountId == null || accountId.isBlank()) {
            throw new BusinessException(ErrorCode.LOGIN_REQUIRED);
        }
        return new AuthPrincipal(accountId.trim(), null);
    }

    public static String of(HttpServletRequest request) {
        return principal(request).accountId();
    }
}
