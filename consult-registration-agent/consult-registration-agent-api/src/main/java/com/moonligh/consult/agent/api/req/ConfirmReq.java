package com.moonligh.consult.agent.api.req;

/**
 * 确认当前咨询卡片。
 */
public record ConfirmReq(
        /** 要确认的那一轮咨询。 */
        String consultationId) {
}
