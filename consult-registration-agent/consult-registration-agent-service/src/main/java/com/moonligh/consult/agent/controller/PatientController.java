package com.moonligh.consult.agent.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.moonligh.consult.agent.api.req.PatientCreateReq;
import com.moonligh.consult.agent.api.res.PatientRes;
import com.moonligh.consult.agent.service.PatientService;
import com.moonligh.consult.framework.Result;
import com.moonligh.consult.security.AccountIds;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 单测用就诊人入口。生产列表和新建在身份服务。
 */
@RequestMapping("/api/patients")
public class PatientController {

    private final PatientService service;

    public PatientController(PatientService service) {
        this.service = service;
    }

    /** 列出当前账号已绑定的就诊人。 */
    @GetMapping
    public Result<List<PatientRes>> list(HttpServletRequest request) {
        return Result.ok(service.list(AccountIds.of(request)));
    }

    /** 给当前账号新增一位就诊人。 */
    @PostMapping
    public Result<PatientRes> create(@RequestBody PatientCreateReq request, HttpServletRequest http) {
        return Result.ok(service.create(AccountIds.of(http), request));
    }
}
