package com.moonligh.consult.identity.api;

/**
 * 新建一条出诊。日期 yyyy-MM-dd。
 */
public record ScheduleSaveReq(
        String doctorId,
        String visitDate,
        String period,
        String startTime,
        String endTime,
        String visitType,
        Integer limitNum) {
}
