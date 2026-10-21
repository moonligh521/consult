package com.moonligh.consult.agent.api.res;

import java.util.List;

import com.moonligh.consult.agent.api.enums.Department;
import com.moonligh.consult.agent.api.enums.Gender;

/**
 * 咨询卡片。性别、年龄、病程齐了之后才会生成，给患者确认或修改。
 */
public record ConsultationCardRes(
        /** 性别。 */
        Gender gender,
        /** 年龄。 */
        Integer age,
        /** 病程。 */
        String duration,
        /** 整理后的病情描述。 */
        String description,
        /** 抽出的症状标签。 */
        List<String> symptoms,
        /** 推荐科室。 */
        Department department) {
}
