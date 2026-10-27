package com.moonligh.consult.agent.api.req;

import com.moonligh.consult.agent.api.enums.Gender;

/**
 * 修改咨询卡片。只改传入的字段，没传的保持原值。至少改一项。
 */
public record CardPatchReq(
        /** 要改的那一轮咨询。 */
        String consultationId,
        /** 性别，JSON 传 1 或 2。 */
        Gender gender,
        /** 年龄，1 到 120。 */
        Integer age,
        /** 病程，例如「半年」。 */
        String duration,
        /** 病情描述。 */
        String description,
        /** 一级类 code。 */
        Integer department,
        /** 二级挂号科名。和 department 一起改咨询科室。 */
        String specialty) {

    public CardPatchReq(
            String consultationId,
            Gender gender,
            Integer age,
            String duration,
            String description,
            Integer department) {
        this(consultationId, gender, age, duration, description, department, null);
    }
}
