package com.moonligh.consult.agent.domain.department;

/**
 * 某二级科的出诊覆盖。开通门禁用。
 */
public record DoctorCoverage(int doctorCount, int onDutyToday, int remainSlotsToday) {

    public boolean enough() {
        return onDutyToday >= 2 && remainSlotsToday > 0;
    }
}
