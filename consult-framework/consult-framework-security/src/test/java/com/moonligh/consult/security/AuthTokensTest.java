package com.moonligh.consult.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

class AuthTokensTest {

    private final AuthTokens tokens = new AuthTokens("consult-dev-jwt-secret-key-32bytes!", Duration.ofHours(1));

    @Test
    void issueAndParse() {
        String jwt = tokens.issue("acc-1", "13800000000");
        AuthPrincipal principal = tokens.parse(jwt);
        assertEquals("acc-1", principal.accountId());
        assertEquals("13800000000", principal.mobile());
        assertEquals(AuthPrincipal.PATIENT, principal.role());
        assertEquals("acc-1", tokens.parse("Bearer " + jwt).accountId());
        AuthPrincipal staff = tokens.parse(tokens.issue("st-1", "admin", AuthPrincipal.STAFF));
        assertEquals(AuthPrincipal.STAFF, staff.role());
    }

    @Test
    void rejectBlank() {
        BusinessException error = assertThrows(BusinessException.class, () -> tokens.parse(" "));
        assertEquals(ErrorCode.LOGIN_REQUIRED, error.getErrorCode());
    }
}
