package com.moonligh.consult.agent.api.event;

/**
 * 挂号派单成功后同步医生端。不含病情正文。
 */
public record OrderAssignedEvent(
        String orderId,
        String consultationId,
        String doctorId,
        String specialty,
        String scheduleId) {
}
