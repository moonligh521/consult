package com.moonligh.consult.identity.api;

/** 医生待接箱里的一单。 */
public record DoctorInboxRes(String orderId, String consultationId, String specialty, String scheduleId) {
}
