package com.moonligh.consult.agent.api.req;

/**
 * 患者结束问诊。reason 空则按 ENDED。
 */
public record EndConsultReq(String consultationId, String reason) {
}
