package com.moonligh.consult.identity.api;

/**
 * 新建或修改医生。一级科室、二级科室、擅长分开填。
 */
public record DoctorSaveReq(
        String name,
        Integer gender,
        String title,
        String hospital,
        Integer departmentCode,
        String specialty,
        String skills,
        String intro,
        Integer status) {
}
