package com.moonligh.consult.identity.service;

import org.springframework.stereotype.Service;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.consult.identity.api.StaffLoginRes;
import com.moonligh.consult.identity.api.StaffMeRes;
import com.moonligh.consult.identity.infrastructure.persistence.StaffStore;
import com.moonligh.consult.identity.infrastructure.persistence.StaffStore.StaffRow;
import com.moonligh.consult.security.AuthPrincipal;
import com.moonligh.consult.security.AuthTokens;

/**
 * 管理端登录。
 */
@Service
public class StaffService {

    private final StaffStore store;
    private final AuthTokens tokens;

    public StaffService(StaffStore store, AuthTokens tokens) {
        this.store = store;
        this.tokens = tokens;
    }

    public StaffLoginRes login(String username, String password) {
        String name = username == null ? "" : username.trim();
        if (name.isEmpty() || password == null || password.isBlank()) {
            throw new BusinessException(ErrorCode.STAFF_LOGIN_FAILED);
        }
        StaffRow staff = store.findByUsername(name);
        if (staff == null || staff.status() != 1 || !store.matches(password, staff.passwordHash())) {
            throw new BusinessException(ErrorCode.STAFF_LOGIN_FAILED);
        }
        String token = tokens.issue(staff.id(), staff.username(), AuthPrincipal.STAFF, staff.doctorId());
        return new StaffLoginRes(token, staff.id(), staff.username(), staff.name(), tokens.ttlSeconds(), staff.doctorId());
    }

    public StaffMeRes current(String accountId) {
        StaffRow row = store.findById(accountId);
        if (row == null) {
            throw new BusinessException(ErrorCode.LOGIN_REQUIRED);
        }
        return new StaffMeRes(row.id(), row.username(), row.name(), row.doctorId());
    }
}
