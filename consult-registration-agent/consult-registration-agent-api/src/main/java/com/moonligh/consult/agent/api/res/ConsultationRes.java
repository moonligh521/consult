package com.moonligh.consult.agent.api.res;

import com.moonligh.consult.agent.api.enums.ConsultationStatus;
import com.moonligh.consult.agent.api.enums.MissingSlot;

public record ConsultationRes(
        String consultationId,
        ConsultationStatus status,
        String prompt,
        MissingSlot missingSlot,
        ConsultationCardRes card) {
}
