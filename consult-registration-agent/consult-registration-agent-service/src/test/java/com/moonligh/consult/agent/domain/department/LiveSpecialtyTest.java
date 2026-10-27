package com.moonligh.consult.agent.domain.department;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.moonligh.consult.agent.api.enums.Department;
import com.moonligh.consult.agent.domain.DepartmentRouter;
import com.moonligh.consult.agent.domain.RegistrationAgent;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

class LiveSpecialtyTest {

    @Test
    void disabledSpecialtyLeavesMatchingIndex() {
        DepartmentSnapshot snapshot = MemoryDepartmentCatalog.seed()
                .withoutSpecialty("呼吸内科")
                .snapshot();
        assertFalse(snapshot.isLiveSpecialty(Department.INTERNAL.getCode(), "呼吸内科"));
        assertTrue(snapshot.isLiveSpecialty(Department.INTERNAL.getCode(), "消化内科"));
        assertTrue(snapshot.symptomPhrases().contains("胃痛"));
    }

    @Test
    void coughDoesNotHangDisabledRespiratory() {
        DepartmentRouter router = new DepartmentRouter(MemoryDepartmentCatalog.seed().withoutSpecialty("呼吸内科"));
        assertFalse("呼吸内科".equals(router.advise("干咳半年").specialty()));
        assertEquals("消化内科", router.advise("胃痛三天").specialty());
    }

    @Test
    void confirmRejectsDisabledSpecialty() {
        RegistrationAgent agent = new RegistrationAgent(
                MemoryDepartmentCatalog.seed().withoutSpecialty("呼吸内科"),
                new DepartmentRouter(MemoryDepartmentCatalog.seed().withoutSpecialty("呼吸内科")),
                com.moonligh.consult.agent.domain.triage.ComplaintModel.unavailable());
        BusinessException error = assertThrows(
                BusinessException.class,
                () -> agent.confirm(Department.INTERNAL.getCode(), "呼吸内科"));
        assertEquals(ErrorCode.INVALID_DEPARTMENT, error.getErrorCode());
    }

    @Test
    void confirmKeepsLiveSpecialty() {
        RegistrationAgent agent = new RegistrationAgent(
                MemoryDepartmentCatalog.seed(),
                new DepartmentRouter(MemoryDepartmentCatalog.seed()),
                com.moonligh.consult.agent.domain.triage.ComplaintModel.unavailable());
        DepartmentRouter.Advice advice = agent.confirm(Department.INTERNAL.getCode(), "消化内科");
        assertEquals(Department.INTERNAL, advice.department());
        assertEquals("消化内科", advice.specialty());
    }
}
