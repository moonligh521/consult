package com.moonligh.consult.identity.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.consult.framework.Result;
import com.moonligh.consult.identity.api.AssignDoctorReq;
import com.moonligh.consult.identity.api.AssignDoctorRes;
import com.moonligh.consult.identity.api.DoctorCoverageRes;
import com.moonligh.consult.identity.api.ExtendDoctorReq;
import com.moonligh.consult.identity.api.ReleaseDoctorReq;
import com.moonligh.consult.identity.config.DoctorSeed;
import com.moonligh.consult.identity.service.DoctorAssignService;
import com.moonligh.consult.identity.store.DoctorStore;

/**
 * 挂号服务内部派医生。浏览器进不来，靠 X-Internal-Token（框架过滤器校验）。
 */
@RestController
@RequestMapping("/internal/doctors")
public class InternalDoctorController {

    private final DoctorAssignService assign;
    private final DoctorStore doctors;
    private final DoctorSeed seed;

    public InternalDoctorController(DoctorAssignService assign, DoctorStore doctors, DoctorSeed seed) {
        this.assign = assign;
        this.doctors = doctors;
        this.seed = seed;
    }

    @GetMapping("/coverage")
    public Result<DoctorCoverageRes> coverage(@RequestParam int departmentCode, @RequestParam String specialty) {
        return Result.ok(doctors.coverage(departmentCode, specialty));
    }

    @PostMapping("/ensure-demo-coverage")
    public Result<DoctorCoverageRes> ensureDemo(
            @RequestParam int departmentCode, @RequestParam String specialty) {
        seed.ensureSpecialty(departmentCode, specialty);
        return Result.ok(doctors.coverage(departmentCode, specialty));
    }

    @PostMapping("/assign")
    public Result<AssignDoctorRes> assign(@RequestBody AssignDoctorReq req) {
        return Result.ok(assign.assign(req));
    }

    @PostMapping("/release")
    public Result<Void> release(@RequestBody ReleaseDoctorReq req) {
        if (req == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING);
        }
        assign.release(req.orderId(), req.reason());
        return Result.ok();
    }

    @PostMapping("/extend")
    public Result<Void> extend(@RequestBody ExtendDoctorReq req) {
        if (req == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING);
        }
        assign.extend(req.orderId(), req.consultMinutes());
        return Result.ok();
    }
}
