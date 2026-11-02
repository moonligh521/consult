package com.moonligh.consult.agent.api.req;

/**
 * 问诊进行中改时长。
 */
public record ConsultSessionReq(String consultationId, Integer consultMinutes) {
}
