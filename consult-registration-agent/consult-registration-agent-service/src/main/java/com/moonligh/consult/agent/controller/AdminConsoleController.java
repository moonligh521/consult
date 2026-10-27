package com.moonligh.consult.agent.controller;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.moonligh.consult.agent.api.req.DepartmentSaveReq;
import com.moonligh.consult.agent.api.req.SpecialtySaveReq;
import com.moonligh.consult.agent.api.res.AdminConsultationRes;
import com.moonligh.consult.agent.api.res.CatalogImpactRes;
import com.moonligh.consult.agent.api.res.DepartmentTreeRes;
import com.moonligh.consult.agent.infrastructure.persistence.ConsultationMapper;
import com.moonligh.consult.agent.service.CatalogChangeService;
import com.moonligh.consult.framework.Result;

/**
 * HIS 管理端：咨询会话和科室目录。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminConsoleController {

    private final ConsultationMapper mapper;
    private final CatalogChangeService catalog;

    public AdminConsoleController(ConsultationMapper mapper, CatalogChangeService catalog) {
        this.mapper = mapper;
        this.catalog = catalog;
    }

    /** 时间窗内最近更新的咨询。不传默认近 7 天，窗口最长 1 年，最多 200 条；不允许无时间条件扫全表。 */
    @GetMapping("/consultations")
    public Result<List<AdminConsultationRes>> consultations(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "100") int limit) {
        Instant end = to == null ? Instant.now() : to;
        Instant start = from == null ? end.minus(7, ChronoUnit.DAYS) : from;
        if (start.isBefore(end.minus(365, ChronoUnit.DAYS))) {
            start = end.minus(365, ChronoUnit.DAYS);
        }
        int size = Math.max(1, Math.min(limit, 200));
        return Result.ok(mapper.listRecent(start, end, size));
    }

    @GetMapping("/departments")
    public Result<List<DepartmentTreeRes>> departments() {
        return Result.ok(catalog.adminTree());
    }

    @GetMapping("/departments/impact")
    public Result<CatalogImpactRes> impact(
            @RequestParam int code,
            @RequestParam(required = false) Long specialtyId) {
        return Result.ok(catalog.impact(code, specialtyId));
    }

    @PostMapping("/departments")
    public Result<Integer> addDepartment(@RequestBody DepartmentSaveReq req) {
        return Result.ok(catalog.addDepartment(req));
    }

    @PatchMapping("/departments/{code}")
    public Result<Void> patchDepartment(@PathVariable int code, @RequestBody DepartmentSaveReq req) {
        catalog.patchDepartment(code, req);
        return Result.ok(null);
    }

    @PostMapping("/departments/{code}/specialties")
    public Result<Long> addSpecialty(@PathVariable int code, @RequestBody SpecialtySaveReq req) {
        return Result.ok(catalog.addSpecialty(code, req));
    }

    @PatchMapping("/departments/{code}/specialties/{id}")
    public Result<Void> patchSpecialty(
            @PathVariable int code,
            @PathVariable long id,
            @RequestBody SpecialtySaveReq req) {
        catalog.patchSpecialty(code, id, req);
        return Result.ok(null);
    }
}
