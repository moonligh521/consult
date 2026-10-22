package com.moonligh.consult.agent.api.req;

import com.moonligh.consult.agent.api.enums.Department;
import com.moonligh.consult.agent.api.enums.Gender;

/**
 * 修改咨询卡片。只改传入的字段，没传的保持原值。至少改一项。
 */
public record CardPatchReq(
        String consultationId,
        Gender gender,
        Integer age,
        String duration,
        String description,
        Department department) {
}
