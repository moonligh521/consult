package com.moonligh.consult.identity.api;

/**
 * 派单结果。挂上即占号源和并发。matchSource 见 DoctorMatcher.MatchSource。
 */
public record AssignDoctorRes(
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
}
