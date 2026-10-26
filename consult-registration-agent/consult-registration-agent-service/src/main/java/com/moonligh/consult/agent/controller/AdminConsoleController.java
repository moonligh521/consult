package com.moonligh.consult.agent.controller;

import java.util.Comparator;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.moonligh.consult.agent.api.res.AdminConsultationRes;
import com.moonligh.consult.agent.api.res.AdminDepartmentRes;
import com.moonligh.consult.agent.domain.department.DepartmentCatalog;
import com.moonligh.consult.agent.domain.department.DepartmentSpecialty;
import com.moonligh.consult.agent.infrastructure.persistence.ConsultationMapper;
import com.moonligh.consult.framework.Result;

/**
 * HIS 管理端：咨询会话和科室目录。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminConsoleController {

    private final ConsultationMapper mapper;
    private final DepartmentCatalog catalog;

    public AdminConsoleController(ConsultationMapper mapper, DepartmentCatalog catalog) {
        this.mapper = mapper;
        this.catalog = catalog;
    }

    @GetMapping("/consultations")
    public Result<List<AdminConsultationRes>> consultations() {
        return Result.ok(mapper.listRecent());
    }

    @GetMapping("/departments")
    public Result<List<AdminDepartmentRes>> departments() {
        return Result.ok(catalog.snapshot().enabled().stream()
                .sorted(Comparator.comparingInt(item -> item.code()))
                .map(item -> new AdminDepartmentRes(
                        item.code(),
                        item.name(),
                        item.enabled(),
                        item.specialties().size(),
                        item.specialties().stream().map(DepartmentSpecialty::name).toList()))
                .toList());
    }
}
