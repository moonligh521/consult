package com.moonligh.consult.agent.api.req;

/**
 * 把账号下的一位就诊人绑到本轮咨询。
 */
public record BindPatientReq(
        /** 要绑的那一轮咨询。 */
        String consultationId,
        /** 就诊人编号。 */
        String patientId) {
}
