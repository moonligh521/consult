package com.moonligh.consult.agent.domain;

import com.moonligh.consult.agent.api.enums.Department;
import com.moonligh.consult.agent.api.res.ConsultationCardRes;
import com.moonligh.consult.agent.domain.triage.DecisionSource;

/**
 * 推荐门禁。二级科没有在线医生时改推荐全科；全科始终留下。
 */
public final class SpecialtyGate {

    private SpecialtyGate() {
    }

    public static boolean accepting(String specialty, SpecialtyPresence presence) {
        if (specialty == null || specialty.isBlank() || "全科".equals(specialty)) {
            return true;
        }
        return presence == null || presence.online(specialty);
    }

    public static DepartmentRouter.Advice recommend(DepartmentRouter.Advice advice, SpecialtyPresence presence) {
        if (advice == null || accepting(advice.specialty(), presence)) {
            return advice;
        }
        return new DepartmentRouter.Advice(Department.GENERAL, "全科", null);
    }

    public static ConsultationCardRes retarget(ConsultationCardRes card, SpecialtyPresence presence) {
        if (card == null || accepting(card.specialty(), presence)) {
            return card;
        }
        return new ConsultationCardRes(
                card.gender(),
                card.age(),
                card.duration(),
                card.description(),
                card.symptoms(),
                Department.GENERAL.getCode(),
                "全科",
                card.associatedSymptoms(),
                card.allergy(),
                card.medications(),
                card.departmentEvidence(),
                card.patientId(),
                card.patientName(),
                DecisionSource.FALLBACK.name(),
                false);
    }
}
