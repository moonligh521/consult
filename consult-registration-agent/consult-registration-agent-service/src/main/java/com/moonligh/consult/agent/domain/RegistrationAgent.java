package com.moonligh.consult.agent.domain;

import com.moonligh.consult.agent.api.enums.Department;
import com.moonligh.consult.agent.api.enums.Gender;
import com.moonligh.consult.agent.domain.department.DepartmentCatalog;
import com.moonligh.consult.agent.domain.department.DepartmentRecord;
import com.moonligh.consult.agent.domain.department.DepartmentSnapshot;
import com.moonligh.consult.agent.domain.department.MemoryDepartmentCatalog;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

/**
 * 工具型挂号 Agent：先走纠错+关键词快路径，对不上词才允许模型建议科室。
 * 急症仍由 SafetyGate 先判；这里不能升 120、不能超出已开通科室。
 */
public class RegistrationAgent {

    private final DepartmentRouter router;
    private final DepartmentAdvisor advisor;
    private final DepartmentCatalog catalog;

    public RegistrationAgent(DepartmentRouter router) {
        this(router, DepartmentAdvisor.disabled(), MemoryDepartmentCatalog.seed());
    }

    public RegistrationAgent(DepartmentRouter router, DepartmentAdvisor advisor, DepartmentCatalog catalog) {
        this.router = router;
        this.advisor = advisor == null ? DepartmentAdvisor.disabled() : advisor;
        this.catalog = catalog;
    }

    /** 推荐科室。有关键词或不确定话术时不调模型。 */
    public DepartmentRouter.Advice advise(String description, Integer age, Gender gender) {
        DepartmentSnapshot snapshot = catalog.snapshot();
        DepartmentRouter.Advice fast = router.advise(description, age, gender);
        if (!needsAdvisor(description, fast, snapshot)) {
            return fast;
        }
        return advisor.suggest(description, age, gender)
                .map(slow -> clamp(slow, snapshot))
                .orElse(fast);
    }

    public DepartmentRouter.Advice advise(String description) {
        return advise(description, null, null);
    }

    static boolean needsAdvisor(String description, DepartmentRouter.Advice fast, DepartmentSnapshot snapshot) {
        if (description == null || description.isBlank()) {
            return false;
        }
        if (SpokenNormalizer.uncertainDepartment(description, snapshot)) {
            return false;
        }
        return fast.evidence() == null;
    }

    /** 患者在卡片上确认的二级科。specialty 对不上时落到该一级类的默认二级。 */
    public DepartmentRouter.Advice confirm(Department department, String specialty) {
        DepartmentSnapshot snapshot = catalog.snapshot();
        Department enabled = snapshot.requireEnabled(department.getCode());
        String resolved = snapshot.resolveSpecialty(enabled, specialty);
        return new DepartmentRouter.Advice(enabled, resolved, "患者确认科室");
    }

    public DepartmentRouter.Advice confirmSpecialty(String specialty) {
        DepartmentSnapshot snapshot = catalog.snapshot();
        DepartmentRecord parent = snapshot.parentOfSpecialty(specialty);
        if (parent == null) {
            throw new BusinessException(ErrorCode.INVALID_DEPARTMENT);
        }
        return confirm(parent.toDepartment(), specialty);
    }

    private static DepartmentRouter.Advice clamp(DepartmentRouter.Advice advice, DepartmentSnapshot snapshot) {
        if (advice == null || advice.department() == null) {
            var fallback = snapshot.fallback();
            return new DepartmentRouter.Advice(fallback.toDepartment(), fallback.defaultSpecialty(), null);
        }
        if (!snapshot.isEnabled(advice.department().getCode())) {
            var fallback = snapshot.fallback();
            return new DepartmentRouter.Advice(fallback.toDepartment(), fallback.defaultSpecialty(), null);
        }
        String specialty = snapshot.resolveSpecialty(advice.department(), advice.specialty());
        return new DepartmentRouter.Advice(advice.department(), specialty, advice.evidence());
    }
}
