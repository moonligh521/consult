package com.moonligh.consult.identity.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.moonligh.consult.framework.Result;
import com.moonligh.consult.identity.api.StaffLoginReq;
import com.moonligh.consult.identity.api.StaffLoginRes;
import com.moonligh.consult.identity.api.StaffMeRes;
import com.moonligh.consult.identity.service.StaffService;
import com.moonligh.consult.identity.store.StaffStore;
import com.moonligh.consult.identity.store.StaffStore.StaffRow;
import com.moonligh.consult.security.AccountIds;

import jakarta.servlet.http.HttpServletRequest;

/**
 * HIS 管理端登录与当前工作人员。
 */
@RestController
@RequestMapping("/api/admin")
public class StaffAuthController {

    private final StaffService staffs;
    private final StaffStore store;

    public StaffAuthController(StaffService staffs, StaffStore store) {
        this.staffs = staffs;
        this.store = store;
    }

    @PostMapping("/login")
    public Result<StaffLoginRes> login(@RequestBody StaffLoginReq request) {
        if (request == null) {
            return Result.ok(staffs.login(null, null));
        }
        return Result.ok(staffs.login(request.username(), request.password()));
    }

    @GetMapping("/me")
    public Result<StaffMeRes> me(HttpServletRequest request) {
        StaffRow row = store.findById(AccountIds.of(request));
        if (row == null) {
            throw new com.moonligh.consult.framework.BusinessException(
                    com.moonligh.consult.framework.ErrorCode.LOGIN_REQUIRED);
        }
        return Result.ok(new StaffMeRes(row.id(), row.username(), row.name()));
    }
}
