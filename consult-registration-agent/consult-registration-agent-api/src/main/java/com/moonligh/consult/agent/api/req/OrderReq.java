package com.moonligh.consult.agent.api.req;

/**
 * 卡片确认后选择咨询档位并下单。
 */
public record OrderReq(
        /** 要下单的那一轮咨询。 */
        String consultationId,
        /** 档位。1 普通咨询，2 极速咨询，3 三甲医生，4 权威专家。 */
        Integer packageCode,
        /** 问诊时长分钟。5/10/15/20/30，空则 15。 */
        Integer consultMinutes) {

    public OrderReq(String consultationId, Integer packageCode) {
        this(consultationId, packageCode, null);
    }
}
