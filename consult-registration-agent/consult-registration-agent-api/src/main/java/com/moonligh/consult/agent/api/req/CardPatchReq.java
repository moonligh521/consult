package com.moonligh.consult.agent.api.req;

import com.moonligh.consult.agent.api.enums.Department;
import com.moonligh.consult.agent.api.enums.Gender;

public record CardPatchReq(
        String consultationId,
        Gender gender,
        Integer age,
        String duration,
        String description,
        Department department) {
}
