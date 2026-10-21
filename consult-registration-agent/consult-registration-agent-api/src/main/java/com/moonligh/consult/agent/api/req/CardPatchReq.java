package com.moonligh.consult.agent.api.req;

import com.moonligh.consult.agent.api.enums.Department;
import com.moonligh.consult.agent.api.enums.Gender;

/**
 * 修改咨询卡片。对应 PATCH /api/consultations/card。
 * 只改传入的字段，没传的保持原值。至少改一项。
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
        /** 推荐科室，JSON 传科室 code。 */
        Department department) {
}
