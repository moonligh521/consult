package com.moonligh.consult.agent.api.req;

/**
 * 支付后未派到医生时，再派一次。
 */
public record ReassignReq(String consultationId) {
}
