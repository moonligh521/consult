package com.moonligh.consult.agent.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.moonligh.consult.agent.api.res.DepartmentTreeRes;
import com.moonligh.consult.agent.service.CatalogChangeService;
import com.moonligh.consult.framework.Result;

/**
 * 患者选科树。只返回开通的一级和二级。
 */
@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private final CatalogChangeService catalog;

    public DepartmentController(CatalogChangeService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public Result<List<DepartmentTreeRes>> tree() {
        return Result.ok(catalog.patientTree());
    }
}
