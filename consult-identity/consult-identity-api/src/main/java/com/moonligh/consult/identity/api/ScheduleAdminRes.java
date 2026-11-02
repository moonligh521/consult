package com.moonligh.consult.identity.api;

/**
 * 管理端排班一行。
 */
public record ScheduleAdminRes(
        String id,
        String doctorId,
        String doctorName,
        String specialty,
        String visitDate,
        int weekday,
        String period,
        String startTime,
        String endTime,
        String visitType,
        int limitNum,
        int remainNum,
        boolean stopped) {
}
