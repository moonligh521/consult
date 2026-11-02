package com.moonligh.consult.agent.api.res;

/**
 * 支付后派到的医生。一级科室码、二级科室分开。
 * matchSource：SKILL_CANON 擅长对上归约病名，SKILL_TEXT 擅长对上原话，LOAD_* / GENERAL 按负载兜底。
 */
public record AssignedDoctorRes(
        String id,
        String name,
        String title,
        String hospital,
        int departmentCode,
        String specialty,
        String skills,
        String evidence,
        String scheduleId,
        String matchSource) {

    public AssignedDoctorRes(
            String id,
            String name,
            String title,
            String hospital,
            int departmentCode,
            String specialty,
            String skills,
            String evidence,
            String scheduleId) {
        this(id, name, title, hospital, departmentCode, specialty, skills, evidence, scheduleId, null);
    }
}
