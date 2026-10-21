package com.moonligh.consult.agent.domain.safety;

import java.time.Instant;

/**
 * 一条只追加的原话。
 */
public record ConsultationMessage(
        int seq,
        String messageId,
        MessageSource source,
        String text,
        Instant receivedAt) {
}
