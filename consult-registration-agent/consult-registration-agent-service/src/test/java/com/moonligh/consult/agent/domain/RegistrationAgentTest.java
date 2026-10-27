package com.moonligh.consult.agent.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.moonligh.consult.agent.api.enums.Department;
import com.moonligh.consult.agent.domain.department.MemoryDepartmentCatalog;
import com.moonligh.consult.framework.model.ModelStatus;
import com.moonligh.consult.agent.domain.triage.ComplaintModel;
import com.moonligh.consult.agent.domain.triage.ComplaintReply;
import com.moonligh.consult.agent.domain.triage.DecisionSource;
import com.moonligh.consult.agent.domain.triage.TriageInput;
import com.moonligh.consult.agent.domain.triage.TriageResult;

class RegistrationAgentTest {

    private final DepartmentRouter router = new DepartmentRouter(MemoryDepartmentCatalog.seed());

    private RegistrationAgent agent(ComplaintModel model) {
        return new RegistrationAgent(MemoryDepartmentCatalog.seed(), router, model);
    }

    @Test
    void fastPathKeepsKeywordHit() {
        RegistrationAgent agent = agent((d, a, g, terms, candidates) -> fail("规则已定，不应调模型"));
        DepartmentRouter.Advice advice = agent.advise("干咳半年");
        assertEquals(Department.INTERNAL, advice.department());
        assertEquals("呼吸内科", advice.specialty());
        assertEquals("干咳", advice.evidence());
    }

    @Test
    void unavailableModelLeavesUnmatchedOnGeneral() {
        TriageResult result = agent(ComplaintModel.unavailable())
                .triage(new TriageInput("体检报告看不懂", null, null)).result();
        assertEquals(Department.GENERAL, result.route().advice().department());
        assertEquals("全科", result.route().advice().specialty());
        assertNull(result.route().advice().evidence());
        assertEquals(DecisionSource.FALLBACK, result.route().source());
    }

    @Test
    void modelPicksCandidateWhenRuleCannot() {
        ComplaintModel model = (d, a, g, terms, candidates) -> new ComplaintReply(
                ModelStatus.SUCCESS, List.of(), "呼吸内科", null, 5, false, "test", "stub");
        TriageResult result = agent(model).triage(new TriageInput("体检报告看不懂", null, null)).result();
        assertEquals(Department.INTERNAL, result.route().advice().department());
        assertEquals("呼吸内科", result.route().advice().specialty());
        assertEquals(DecisionSource.MODEL, result.route().source());
    }

    @Test
    void modelNotCalledOnUncertainty() {
        RegistrationAgent agent = agent((d, a, g, terms, candidates) -> fail("不确定话术不应调模型"));
        DepartmentRouter.Advice advice = agent.advise("肚子疼，说不清哪个位置");
        assertEquals(Department.GENERAL, advice.department());
        assertEquals("全科", advice.specialty());
        assertNull(advice.evidence());
    }
}
