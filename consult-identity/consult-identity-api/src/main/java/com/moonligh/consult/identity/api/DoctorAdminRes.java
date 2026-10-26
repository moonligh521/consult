package com.moonligh.consult.identity.api;

/**
 * 管理端医生一行。一级科室码、二级科室、擅长分列。
 */
public record DoctorAdminRes(
        String id,
        String name,
        int gender,
        String title,
        String hospital,
        int departmentCode,
        String specialty,
        String skills,
        String intro,
        int inFlight,
        int concurrentLimit,
        int status) {
}
