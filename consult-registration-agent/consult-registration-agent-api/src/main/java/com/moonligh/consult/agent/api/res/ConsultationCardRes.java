package com.moonligh.consult.agent.api.res;

import java.util.List;

import com.moonligh.consult.agent.api.enums.Department;
import com.moonligh.consult.agent.api.enums.Gender;

public record ConsultationCardRes(
        Gender gender,
        Integer age,
        String duration,
        String description,
        List<String> symptoms,
        Department department) {
}
