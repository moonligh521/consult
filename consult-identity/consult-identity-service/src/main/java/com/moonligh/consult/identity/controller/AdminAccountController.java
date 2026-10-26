package com.moonligh.consult.identity.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.moonligh.consult.framework.Result;
import com.moonligh.consult.identity.api.AccountAdminRes;
import com.moonligh.consult.identity.api.AdminPersonRes;
import com.moonligh.consult.identity.service.AdminQueryService;

/**
 * HIS 管理端：患者账号与就诊人。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminAccountController {

    private final AdminQueryService service;

    public AdminAccountController(AdminQueryService service) {
        this.service = service;
    }

    @GetMapping("/accounts")
    public Result<List<AccountAdminRes>> accounts() {
        return Result.ok(service.accounts());
    }

    @GetMapping("/patients")
    public Result<List<AdminPersonRes>> patients(@RequestParam(required = false) String accountId) {
        return Result.ok(service.patients(accountId));
    }
}
