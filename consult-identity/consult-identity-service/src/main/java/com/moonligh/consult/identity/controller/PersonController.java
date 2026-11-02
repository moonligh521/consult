package com.moonligh.consult.identity.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.moonligh.consult.agent.api.req.PatientCreateReq;
import com.moonligh.consult.agent.api.res.PatientRes;
import com.moonligh.consult.framework.Result;
import com.moonligh.consult.identity.service.PersonService;
import com.moonligh.consult.security.AccountIds;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 当前登录账号下的就诊人。
 */
@RestController
@RequestMapping("/api/patients")
public class PersonController {

    private final PersonService service;

    public PersonController(PersonService service) {
        this.service = service;
    }

    @GetMapping
    public Result<List<PatientRes>> list(HttpServletRequest request) {
        return Result.ok(service.list(AccountIds.of(request)));
    }

    @PostMapping
    public Result<PatientRes> create(@RequestBody PatientCreateReq request, HttpServletRequest http) {
        return Result.ok(service.create(AccountIds.of(http), request));
    }
}
