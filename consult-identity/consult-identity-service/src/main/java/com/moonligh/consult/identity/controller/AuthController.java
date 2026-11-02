package com.moonligh.consult.identity.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.moonligh.consult.framework.Result;
import com.moonligh.consult.identity.api.LoginReq;
import com.moonligh.consult.identity.api.LoginRes;
import com.moonligh.consult.identity.api.SmsReq;
import com.moonligh.consult.identity.service.AuthService;

/**
 * 登录入口。进咨询前必须先走这里。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/sms")
    public Result<Void> sms(@RequestBody SmsReq request) {
        service.sendSms(request == null ? null : request.mobile());
        return Result.ok();
    }

    @PostMapping("/login")
    public Result<LoginRes> login(@RequestBody LoginReq request) {
        if (request == null) {
            return Result.ok(service.login(null, null));
        }
        return Result.ok(service.login(request.mobile(), request.captcha()));
    }
}
