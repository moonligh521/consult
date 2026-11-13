package com.moonligh.consult.identity.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.moonligh.consult.framework.Result;
import com.moonligh.consult.identity.service.DoctorAssignService;
import com.moonligh.consult.security.AuthTokens;
import com.moonligh.consult.security.DoctorDeskGuard;

import jakarta.servlet.http.HttpServletRequest;

/** 接诊台自己点的上线、下线。令牌里没绑定医生就不能点。 */
@RestController
@RequestMapping("/api/doctor/presence")
public class DoctorPresenceController {

    private final DoctorAssignService assign;

    public DoctorPresenceController(DoctorAssignService assign) {
        this.assign = assign;
    }

    @GetMapping
    public Result<Boolean> current(HttpServletRequest request) {
        return Result.ok(assign.isOnline(DoctorDeskGuard.required(request.getHeader(AuthTokens.DOCTOR_HEADER))));
    }

    @PostMapping("/online")
    public Result<Void> online(HttpServletRequest request) {
        assign.online(DoctorDeskGuard.required(request.getHeader(AuthTokens.DOCTOR_HEADER)));
        return Result.ok();
    }

    @PostMapping("/offline")
    public Result<Void> offline(HttpServletRequest request) {
        assign.offline(DoctorDeskGuard.required(request.getHeader(AuthTokens.DOCTOR_HEADER)));
        return Result.ok();
    }
}
