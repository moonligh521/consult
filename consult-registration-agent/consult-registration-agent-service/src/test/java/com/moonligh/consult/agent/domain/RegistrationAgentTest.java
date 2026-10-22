package com.moonligh.consult.agent.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.moonligh.consult.agent.api.enums.Department;
import com.moonligh.consult.agent.domain.department.MemoryDepartmentCatalog;

class RegistrationAgentTest {

    private final DepartmentRouter router = new DepartmentRouter(MemoryDepartmentCatalog.seed());

    @Test
    void fastPathKeepsKeywordHit() {
        RegistrationAgent agent = new RegistrationAgent(router);
        DepartmentRouter.Advice advice = agent.advise("干咳半年");
        assertEquals(Department.INTERNAL, advice.department());
        assertEquals("干咳", advice.evidence());
    }

    @Test
    void disabledAdvisorLeavesUnmatchedOnGeneral() {
        RegistrationAgent agent = new RegistrationAgent(router);
        DepartmentRouter.Advice advice = agent.advise("体检报告看不懂");
        assertEquals(Department.GENERAL, advice.department());
        assertNull(advice.evidence());
    }

    @Test
    void advisorFillsKeywordMissWhenEnabled() {
        DepartmentAdvisor advisor = (text, age, gender) -> Optional.of(
                new DepartmentRouter.Advice(Department.INTERNAL, "灰指甲"));
        RegistrationAgent agent = new RegistrationAgent(router, advisor, MemoryDepartmentCatalog.seed());
        DepartmentRouter.Advice advice = agent.advise("灰指甲");
        assertEquals(Department.INTERNAL, advice.department());
        assertEquals("灰指甲", advice.evidence());
    }

    @Test
    void advisorDoesNotOverrideUncertainty() {
        DepartmentAdvisor advisor = (text, age, gender) -> Optional.of(
                new DepartmentRouter.Advice(Department.INTERNAL, "肚子疼"));
        RegistrationAgent agent = new RegistrationAgent(router, advisor, MemoryDepartmentCatalog.seed());
        DepartmentRouter.Advice advice = agent.advise("肚子疼，说不清哪个位置");
        assertEquals(Department.GENERAL, advice.department());
        assertNull(advice.evidence());
    }
}
