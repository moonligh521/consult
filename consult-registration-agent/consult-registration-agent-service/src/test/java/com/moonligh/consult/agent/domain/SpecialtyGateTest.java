package com.moonligh.consult.agent.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import com.moonligh.consult.agent.api.enums.Department;
import com.moonligh.consult.agent.api.enums.Gender;
import com.moonligh.consult.agent.api.res.ConsultationCardRes;
import com.moonligh.consult.agent.domain.triage.DecisionSource;

class SpecialtyGateTest {

    @Test
    void offlineSpecialtyRecommendsGeneral() {
        DepartmentRouter.Advice advice = SpecialtyGate.recommend(
                new DepartmentRouter.Advice(Department.INTERNAL, "呼吸内科", "咳嗽"),
                name -> false);
        assertEquals(Department.GENERAL.getCode(), advice.departmentCode());
        assertEquals("全科", advice.specialty());
    }

    @Test
    void onlineSpecialtyStays() {
        DepartmentRouter.Advice original = new DepartmentRouter.Advice(Department.INTERNAL, "呼吸内科", "咳嗽");
        assertSame(original, SpecialtyGate.recommend(original, name -> true));
    }

    @Test
    void generalStaysWhenPresenceIsEmpty() {
        DepartmentRouter.Advice original = new DepartmentRouter.Advice(Department.GENERAL, "全科", null);
        assertSame(original, SpecialtyGate.recommend(original, name -> false));
    }

    @Test
    void fullSpecialtyStaysBecauseTheDoctorIsStillOnline() {
        DepartmentRouter.Advice original = new DepartmentRouter.Advice(Department.INTERNAL, "呼吸内科", "咳嗽");
        assertSame(original, SpecialtyGate.recommend(original, "呼吸内科"::equals));
    }

    @Test
    void openCardMovesToGeneralBeforeOrder() {
        ConsultationCardRes card = new ConsultationCardRes(
                Gender.FEMALE, 32, "三天", "咳嗽", java.util.List.of(),
                Department.INTERNAL, "呼吸内科", java.util.List.of(), null, null, "咳嗽", null, null);
        ConsultationCardRes opened = SpecialtyGate.retarget(card, name -> false);
        assertEquals(Department.GENERAL.getCode(), opened.department());
        assertEquals("全科", opened.specialty());
        assertEquals(DecisionSource.FALLBACK.name(), opened.departmentSource());
    }
}
