package com.moonligh.consult.agent.domain;

import java.util.List;

import com.moonligh.consult.agent.api.enums.Department;
import com.moonligh.consult.agent.api.enums.Gender;
import com.moonligh.consult.agent.domain.department.DepartmentCatalog;
import com.moonligh.consult.agent.domain.department.DepartmentRecord;
import com.moonligh.consult.agent.domain.department.DepartmentSnapshot;
import com.moonligh.consult.agent.domain.department.MemoryDepartmentCatalog;
import com.moonligh.consult.agent.domain.triage.Certainty;
import com.moonligh.consult.agent.domain.triage.ComplaintModel;
import com.moonligh.consult.agent.domain.triage.DecisionSource;
import com.moonligh.consult.agent.domain.triage.RouteDecision;
import com.moonligh.consult.agent.domain.triage.TriageEngine;
import com.moonligh.consult.agent.domain.triage.TriageInput;
import com.moonligh.consult.agent.domain.triage.TriageRun;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

/**
 * 挂号 Agent：归约与挂科交给 {@link TriageEngine}（规则 → 模型 → 兜底），患者改科在这里落成 PATIENT。
 * 急症仍由 SafetyGate 先判；这里不能升 120、不能超出已开通科室。
 */
public class RegistrationAgent {

    private final TriageEngine engine;
    private final DepartmentCatalog catalog;

    public RegistrationAgent(DepartmentRouter router) {
        this(MemoryDepartmentCatalog.seed(), router, ComplaintModel.unavailable());
    }

    public RegistrationAgent(DepartmentCatalog catalog, DepartmentRouter router, ComplaintModel model) {
        this(new TriageEngine(catalog, router, model), catalog);
    }

    public RegistrationAgent(TriageEngine engine, DepartmentCatalog catalog) {
        this.engine = engine;
        this.catalog = catalog;
    }

    public TriageEngine engine() {
        return engine;
    }

    public DepartmentSnapshot snapshot() {
        return catalog.snapshot();
    }

    public TriageRun triage(TriageInput in) {
        return engine.run(in);
    }

    /** 只要挂科结论时的简写。 */
    public DepartmentRouter.Advice advise(String description, Integer age, Gender gender) {
        return triage(new TriageInput(description, age, gender)).result().route().advice();
    }

    public DepartmentRouter.Advice advise(String description) {
        return advise(description, null, null);
    }

    /** 患者在卡片上确认的二级科。对不上已开通二级时拒绝，不再静默落到默认科。 */
    public DepartmentRouter.Advice confirm(Department department, String specialty) {
        return confirm(department.getCode(), specialty);
    }

    public DepartmentRouter.Advice confirm(int departmentCode, String specialty) {
        DepartmentSnapshot snapshot = catalog.snapshot();
        snapshot.requireLiveSpecialty(departmentCode, specialty);
        DepartmentRecord rec = snapshot.recordOf(departmentCode);
        String resolved = specialty == null || specialty.isBlank() ? rec.defaultSpecialty() : specialty;
        snapshot.requireLiveSpecialty(departmentCode, resolved);
        return DepartmentRouter.Advice.of(rec, resolved, "患者确认科室");
    }

    public DepartmentRouter.Advice confirmSpecialty(String specialty) {
        DepartmentSnapshot snapshot = catalog.snapshot();
        DepartmentRecord parent = snapshot.parentOfSpecialty(specialty);
        if (parent == null) {
            throw new BusinessException(ErrorCode.INVALID_DEPARTMENT);
        }
        return confirm(parent.code(), specialty);
    }

    /** 患者改科后的挂科结论。 */
    public static RouteDecision patientRoute(DepartmentRouter.Advice advice) {
        return new RouteDecision(advice, Certainty.CERTAIN, DecisionSource.PATIENT, RouteDecision.PATIENT_CONFIRM, List.of());
    }
}
