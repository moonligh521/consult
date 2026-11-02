package com.moonligh.consult.agent.controller;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.moonligh.consult.agent.api.req.SynonymSaveReq;
import com.moonligh.consult.agent.api.res.TriageFunnelRes;
import com.moonligh.consult.agent.domain.department.CanonSynonym;
import com.moonligh.consult.agent.domain.department.DepartmentCatalog;
import com.moonligh.consult.agent.domain.department.DepartmentCatalogWriter;
import com.moonligh.consult.agent.domain.department.SynonymStatus;
import com.moonligh.consult.agent.infrastructure.persistence.ConsultationMapper;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.consult.framework.Result;
import com.moonligh.consult.security.AccountIds;

import jakarta.servlet.http.HttpServletRequest;

/**
 * HIS 管理端：同义归约规则的新建与审核，分诊各节点的规则/模型/兜底占比。需要 staff 角色。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminTriageController {

    private final DepartmentCatalog catalog;
    private final DepartmentCatalogWriter writer;
    private final ConsultationMapper mapper;

    public AdminTriageController(DepartmentCatalog catalog, DepartmentCatalogWriter writer, ConsultationMapper mapper) {
        this.catalog = catalog;
        this.writer = writer;
        this.mapper = mapper;
    }

    /** 生效中的同义规则（DRAFT 与 REVIEWED）。 */
    @GetMapping("/canon-synonyms")
    public Result<List<CanonSynonym>> synonyms() {
        return Result.ok(catalog.snapshot().synonyms());
    }

    /** 新建或改一条，保存后是 DRAFT。 */
    @PostMapping("/canon-synonyms")
    public Result<CanonSynonym> save(@RequestBody SynonymSaveReq req) {
        if (req == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING);
        }
        CanonSynonym rule = new CanonSynonym(req.ruleId(), req.target(), req.all(), req.none(), req.pattern(),
                SynonymStatus.DRAFT);
        try {
            return Result.ok(writer.saveSynonym(rule, catalog.snapshot()));
        } catch (IllegalArgumentException error) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
    }

    /** DRAFT → REVIEWED，记审核人。临床复核后才能点。 */
    @PostMapping("/canon-synonyms/{ruleId}/review")
    public Result<Void> review(@PathVariable String ruleId, HttpServletRequest request) {
        try {
            writer.reviewSynonym(ruleId, AccountIds.principal(request).accountId());
        } catch (IllegalArgumentException error) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
        return Result.ok(null);
    }

    @PostMapping("/canon-synonyms/{ruleId}/retire")
    public Result<Void> retire(@PathVariable String ruleId) {
        writer.retireSynonym(ruleId);
        return Result.ok(null);
    }

    /** 最近 hours 小时各节点的来源与原因分布。 */
    @GetMapping("/triage/funnel")
    public Result<List<TriageFunnelRes>> funnel(@RequestParam(defaultValue = "24") int hours) {
        int window = Math.max(1, Math.min(hours, 24 * 31));
        return Result.ok(mapper.funnel(Instant.now().minus(window, ChronoUnit.HOURS)));
    }
}
