package com.moonligh.consult.identity.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.moonligh.consult.framework.Result;
import com.moonligh.consult.identity.service.DoctorAssignService;
import com.moonligh.consult.security.AuthTokens;
import com.moonligh.consult.security.DoctorDeskGuard;

import jakarta.servlet.http.HttpServletRequest;

/** 接诊台心跳。只给令牌里绑定的医生续在班，请求体里的医生编号不算。 */
@RestController
@RequestMapping("/api/doctor/heartbeat")
public class DoctorHeartbeatController {

    private final DoctorAssignService assign;

    public DoctorHeartbeatController(DoctorAssignService assign) {
        this.assign = assign;
    }

    @PostMapping
    public Result<Void> heartbeat(HttpServletRequest request) {
        assign.heartbeat(DoctorDeskGuard.required(request.getHeader(AuthTokens.DOCTOR_HEADER)));
        return Result.ok();
    }
}
