package com.moonligh.consult.identity.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.consult.framework.Result;
import com.moonligh.consult.identity.api.AssignDoctorReq;
import com.moonligh.consult.identity.api.AssignDoctorRes;
import com.moonligh.consult.identity.api.ExtendDoctorReq;
import com.moonligh.consult.identity.api.ReleaseDoctorReq;
import com.moonligh.consult.identity.config.AuthProperties;
import com.moonligh.consult.identity.service.DoctorAssignService;

/**
 * 挂号服务内部派医生。浏览器进不来，靠 X-Internal-Token。
 */
@RestController
@RequestMapping("/internal/doctors")
public class InternalDoctorController {

    private final DoctorAssignService assign;
    private final AuthProperties properties;

    public InternalDoctorController(DoctorAssignService assign, AuthProperties properties) {
        this.assign = assign;
        this.properties = properties;
    }

    @PostMapping("/assign")
    public Result<AssignDoctorRes> assign(
            @RequestHeader("X-Internal-Token") String internalToken,
            @RequestBody AssignDoctorReq req) {
        requireInternal(internalToken);
        return Result.ok(assign.assign(req));
    }

    @PostMapping("/release")
    public Result<Void> release(
            @RequestHeader("X-Internal-Token") String internalToken,
            @RequestBody ReleaseDoctorReq req) {
        requireInternal(internalToken);
        if (req == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING);
        }
        assign.release(req.orderId(), req.reason());
        return Result.ok();
    }

    @PostMapping("/extend")
    public Result<Void> extend(
            @RequestHeader("X-Internal-Token") String internalToken,
            @RequestBody ExtendDoctorReq req) {
        requireInternal(internalToken);
        if (req == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING);
        }
        assign.extend(req.orderId(), req.consultMinutes());
        return Result.ok();
    }

    private void requireInternal(String internalToken) {
        if (internalToken == null || !internalToken.equals(properties.getInternalToken())) {
            throw new BusinessException(ErrorCode.LOGIN_REQUIRED);
        }
    }
}
