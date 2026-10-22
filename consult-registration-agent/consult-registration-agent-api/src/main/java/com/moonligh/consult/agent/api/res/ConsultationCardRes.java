package com.moonligh.consult.agent.api.res;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.moonligh.consult.agent.api.enums.Department;
import com.moonligh.consult.agent.api.enums.Gender;

/**
 * 咨询卡片。性别、年龄、病程齐了之后才会生成，给患者确认或修改。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
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
        Department department,
        /** 伴随症状。 */
        List<String> associatedSymptoms,
        /** 过敏史，描述里提到才有。 */
        String allergy,
        /** 正在使用的药，描述里提到才有。 */
        String medications,
        /** 推荐这个科室的关键词依据。 */
        String departmentEvidence) {

    public ConsultationCardRes {
        symptoms = symptoms == null ? List.of() : List.copyOf(symptoms);
        associatedSymptoms = associatedSymptoms == null ? List.of() : List.copyOf(associatedSymptoms);
    }
}
