package com.moonligh.consult.identity.domain;

import com.moonligh.consult.agent.api.enums.Gender;
import com.moonligh.consult.agent.api.res.PatientRes;

/**
 * 当前账号下的一位就诊人。
 */
public record PersonRecord(
        String id,
        String accountId,
        String name,
        Gender gender,
        int age,
        String relation,
        boolean isDefault) {

    public PatientRes toRes() {
        return new PatientRes(id, name, gender, age, relation, isDefault);
    }
}
