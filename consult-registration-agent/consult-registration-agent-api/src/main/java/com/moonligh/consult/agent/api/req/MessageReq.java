package com.moonligh.consult.agent.api.req;

/**
 * 患者发来的一句话。
 */
public record MessageReq(
        /** 已有咨询的编号。第一次发言不传，服务会新建一轮。 */
        String consultationId,
        /** 客户端幂等键，不超过 64 字符。不传则服务端生成。 */
        String messageId,
        /** 服务类型，JSON 传 1、2 或 3。不传则卡片只作预约前信息整理。 */
        Integer serviceType,
        /** 患者原文，例如「女，32岁，干咳半年」。 */
        String text) {
}
