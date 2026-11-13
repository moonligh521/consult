package com.moonligh.consult.identity.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
import com.moonligh.consult.identity.api.DoctorPresenceReq;
import com.moonligh.consult.identity.api.ExtendDoctorReq;
import com.moonligh.consult.identity.api.ReleaseDoctorReq;
import com.moonligh.consult.identity.config.DoctorSeed;
import com.moonligh.consult.identity.domain.AssignmentNoticeStore;
import com.moonligh.consult.identity.domain.AssignmentNoticeStore.PendingNotice;
import com.moonligh.consult.identity.service.DoctorAssignService;

/**
 * 挂号服务内部派医生。浏览器进不来，靠 X-Internal-Token（框架过滤器校验）。
 */
@RestController
@RequestMapping("/internal/doctors")
public class InternalDoctorController {

    private final DoctorAssignService assign;
    private final DoctorSeed seed;
    private final AssignmentNoticeStore notices;

    public InternalDoctorController(
            DoctorAssignService assign,
            DoctorSeed seed,
            AssignmentNoticeStore notices) {
        this.assign = assign;
        this.seed = seed;
        this.notices = notices;
    }

    @GetMapping("/coverage")
    public Result<DoctorCoverageRes> coverage(@RequestParam int departmentCode, @RequestParam String specialty) {
        return Result.ok(assign.coverage(departmentCode, specialty));
    }

    @GetMapping("/online-specialties")
    public Result<List<String>> onlineSpecialties() {
        return Result.ok(assign.listOnlineSpecialties());
    }

    @GetMapping("/available")
    public Result<Boolean> available(
            @RequestParam int departmentCode,
            @RequestParam String specialty,
            @RequestParam(defaultValue = "0") int packageCode) {
        return Result.ok(assign.available(specialty, departmentCode, packageCode));
    }

    @PostMapping("/ensure-demo-coverage")
    public Result<DoctorCoverageRes> ensureDemo(
            @RequestParam int departmentCode, @RequestParam String specialty) {
        seed.ensureSpecialty(departmentCode, specialty);
        return Result.ok(assign.coverage(departmentCode, specialty));
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

    @PostMapping("/online")
    public Result<Void> online(@RequestBody DoctorPresenceReq req) {
        assign.online(req == null ? null : req.doctorId());
        return Result.ok();
    }

    @PostMapping("/offline")
    public Result<Void> offline(@RequestBody DoctorPresenceReq req) {
        assign.offline(req == null ? null : req.doctorId());
        return Result.ok();
    }

    @PostMapping("/heartbeat")
    public Result<Void> heartbeat(@RequestBody DoctorPresenceReq req) {
        assign.heartbeat(req == null ? null : req.doctorId());
        return Result.ok();
    }

    @GetMapping("/{doctorId}/inbox")
    public Result<List<DoctorInboxItem>> inbox(@PathVariable String doctorId) {
        List<DoctorInboxItem> rows = new ArrayList<>();
        for (PendingNotice notice : notices.inbox(doctorId, 50)) {
            rows.add(new DoctorInboxItem(
                    notice.orderId(),
                    notice.consultationId(),
                    notice.specialty(),
                    notice.scheduleId()));
        }
        return Result.ok(rows);
    }

    public record DoctorInboxItem(String orderId, String consultationId, String specialty, String scheduleId) {
    }
}
