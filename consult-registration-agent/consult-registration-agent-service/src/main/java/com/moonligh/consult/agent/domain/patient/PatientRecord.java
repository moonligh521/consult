package com.moonligh.consult.agent.domain.patient;

import java.time.Instant;

import com.moonligh.consult.agent.api.enums.Gender;
import com.moonligh.consult.agent.api.res.PatientRes;

/**
 * 账号下的一位就诊人档案。
 */
public record PatientRecord(
        String id,
        String accountId,
        String name,
        Gender gender,
        int age,
        String relation,
        boolean isDefault,
        String patientVersion,
        Instant updatedAt) {

    public PatientRes toRes() {
        return new PatientRes(id, name, gender, age, relation, isDefault, patientVersion, updatedAt);
    }
}
