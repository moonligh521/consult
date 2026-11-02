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
        /** 一级类 code，派医生用。目录后加的一级没有枚举。 */
        Integer department,
        /** 二级挂号科，咨询科室展示这个。 */
        String specialty,
        /** 伴随症状。 */
        List<String> associatedSymptoms,
        /** 过敏史，描述里提到才有。 */
        String allergy,
        /** 正在使用的药，描述里提到才有。 */
        String medications,
        /** 推荐这个科室的关键词依据。 */
        String departmentEvidence,
        /** 本轮选用的就诊人编号。没选时为空。 */
        String patientId,
        /** 就诊人姓名快照。没选时为空，页面显示匿名。 */
        String patientName,
        /** 科室推荐来源：RULE 目录规则、MODEL AI 辅助、FALLBACK 兜底全科、PATIENT 患者自选。老卡为空。 */
        String departmentSource,
        /** 当前卡片挂的科已停用，确认前需改科。 */
        boolean stale) {

    public ConsultationCardRes(
            Gender gender,
            Integer age,
            String duration,
            String description,
            List<String> symptoms,
            Department department,
            String specialty,
            List<String> associatedSymptoms,
            String allergy,
            String medications,
            String departmentEvidence,
            String patientId,
            String patientName) {
        this(gender, age, duration, description, symptoms, department == null ? null : department.getCode(), specialty,
                associatedSymptoms, allergy, medications, departmentEvidence, patientId, patientName, null, false);
    }

    public ConsultationCardRes(
            Gender gender,
            Integer age,
            String duration,
            String description,
            List<String> symptoms,
            Integer department,
            String specialty,
            List<String> associatedSymptoms,
            String allergy,
            String medications,
            String departmentEvidence,
            String patientId,
            String patientName,
            String departmentSource) {
        this(gender, age, duration, description, symptoms, department, specialty, associatedSymptoms, allergy,
                medications, departmentEvidence, patientId, patientName, departmentSource, false);
    }

    public ConsultationCardRes withStale(boolean value) {
        return new ConsultationCardRes(
                gender, age, duration, description, symptoms, department, specialty, associatedSymptoms, allergy,
                medications, departmentEvidence, patientId, patientName, departmentSource, value);
    }

    public ConsultationCardRes {
        symptoms = symptoms == null ? List.of() : List.copyOf(symptoms);
        associatedSymptoms = associatedSymptoms == null ? List.of() : List.copyOf(associatedSymptoms);
    }
}
