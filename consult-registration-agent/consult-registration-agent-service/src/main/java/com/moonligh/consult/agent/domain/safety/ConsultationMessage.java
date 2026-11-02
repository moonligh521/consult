package com.moonligh.consult.agent.domain.safety;

import java.time.Instant;

/**
 * 一条只追加的原话。
 */
public record ConsultationMessage(
        /** 本轮里的序号，从 1 开始。 */
        int seq,
        /** 客户端幂等键，或服务端生成的编号。 */
        String messageId,
        /** 患者消息还是改卡描述。 */
        MessageSource source,
        /** 原话正文。 */
        String text,
        /** 收到这句话的时间。 */
        Instant receivedAt) {
}
