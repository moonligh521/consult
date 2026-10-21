package com.moonligh.consult.agent.api.req;

/**
 * 确认当前咨询卡片。对应 POST /api/consultations/confirmation。
 */
public record ConfirmReq(
        /** 要确认的那一轮咨询。 */
        String consultationId) {
}
