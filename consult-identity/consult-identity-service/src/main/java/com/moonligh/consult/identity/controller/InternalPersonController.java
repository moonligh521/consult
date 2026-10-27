package com.moonligh.consult.identity.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.moonligh.consult.agent.api.res.PatientRes;
import com.moonligh.consult.framework.Result;
import com.moonligh.consult.identity.service.PersonService;
import com.moonligh.consult.security.AuthTokens;

/**
 * 挂号服务核对就诊人归属。不走浏览器。内部令牌由框架过滤器校验。
 */
@RestController
@RequestMapping("/internal/persons")
public class InternalPersonController {

    private final PersonService persons;

    public InternalPersonController(PersonService persons) {
        this.persons = persons;
    }

    @GetMapping("/{personId}")
    public Result<PatientRes> find(
            @PathVariable String personId,
            @RequestHeader(AuthTokens.ACCOUNT_HEADER) String accountId) {
        return Result.ok(persons.require(accountId, personId));
    }
}
