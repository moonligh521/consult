package com.moonligh.consult.identity.api;

/**
 * 某二级科的出诊覆盖。
 */
public record DoctorCoverageRes(int doctorCount, int onDutyToday, int remainSlotsToday) {
}
