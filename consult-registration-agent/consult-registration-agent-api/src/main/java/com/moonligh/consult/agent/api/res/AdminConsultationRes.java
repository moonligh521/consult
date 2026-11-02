package com.moonligh.consult.agent.api.res;

/**
 * 管理端咨询会话一行。不含卡片全文。
 */
public record AdminConsultationRes(
        String id,
        int status,
        Integer gender,
        Integer age,
        String patientId,
        String patientName,
        String duration,
        String description,
        String createdAt,
        String updatedAt) {
}
