package com.moonligh.consult.agent.api.req;

/** 医生回复。doctorId 必须是这单已派上的医生。 */
public record DoctorReplyReq(String consultationId, String doctorId, String text) {
}
