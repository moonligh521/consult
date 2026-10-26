package com.moonligh.consult.identity.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.moonligh.consult.framework.Result;
import com.moonligh.consult.identity.api.DoctorAdminRes;
import com.moonligh.consult.identity.api.DoctorSaveReq;
import com.moonligh.consult.identity.api.ScheduleAdminRes;
import com.moonligh.consult.identity.api.ScheduleSaveReq;
import com.moonligh.consult.identity.service.DoctorAdminService;

/**
 * HIS 管理端：医生与排班。
 */
@RestController
@RequestMapping("/api/admin")
public class DoctorAdminController {

    private final DoctorAdminService service;

    public DoctorAdminController(DoctorAdminService service) {
        this.service = service;
    }

    @GetMapping("/doctors")
    public Result<List<DoctorAdminRes>> doctors() {
        return Result.ok(service.listDoctors());
    }

    @PostMapping("/doctors")
    public Result<DoctorAdminRes> createDoctor(@RequestBody DoctorSaveReq req) {
        return Result.ok(service.createDoctor(req));
    }

    @PatchMapping("/doctors/{id}")
    public Result<DoctorAdminRes> updateDoctor(@PathVariable String id, @RequestBody DoctorSaveReq req) {
        return Result.ok(service.updateDoctor(id, req));
    }

    @GetMapping("/schedules")
    public Result<List<ScheduleAdminRes>> schedules(
            @RequestParam(required = false) String doctorId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return Result.ok(service.listSchedules(doctorId, from, to));
    }

    @PostMapping("/schedules")
    public Result<ScheduleAdminRes> createSchedule(@RequestBody ScheduleSaveReq req) {
        return Result.ok(service.createSchedule(req));
    }

    @PatchMapping("/schedules/{id}/stop")
    public Result<Void> stop(@PathVariable String id, @RequestParam(defaultValue = "true") boolean stopped) {
        service.stopSchedule(id, stopped);
        return Result.ok();
    }

    @DeleteMapping("/schedules/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.deleteSchedule(id);
        return Result.ok();
    }
}
