package com.moonligh.consult.agent.api.req;

/**
 * 患者发来的一句话。对应 POST /api/consultations/messages。
 */
public record MessageReq(
        /** 已有咨询的编号。第一次发言不传，服务会新建一轮。 */
        String consultationId,
        /** 患者原文，例如「女，32岁，干咳半年」。 */
        String text) {
}
