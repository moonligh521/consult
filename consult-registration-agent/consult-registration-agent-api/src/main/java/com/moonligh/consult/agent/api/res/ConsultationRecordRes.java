package com.moonligh.consult.agent.api.res;

import java.time.Instant;

/**
 * 咨询记录列表一行。不含诊室全文。
 */
public record ConsultationRecordRes(
        String consultationId,
        String title,
        int status,
        Instant createdAt,
        Instant updatedAt,
        Instant endedAt,
        String doctorName) {
}
