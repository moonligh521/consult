package com.moonligh.consult.identity.domain;

import java.util.List;

/**
 * 派单通知。占坑成功的同一事务写入出站表和医生待接箱。
 */
public interface AssignmentNoticeStore {

    void record(String orderId, String consultationId, String doctorId, String specialty, String scheduleId);

    List<PendingNotice> claimPending(int limit);

    /** 只领这一单。积压留给 assignmentOutboxJob，避免提交后在请求线程上把队列扫空。 */
    PendingNotice claimOne(String orderId);

    void markSent(String orderId);

    /** 发送失败，把本条从「发送中」放回待发，别的实例才能再领。 */
    void releaseClaim(String orderId);

    List<PendingNotice> inbox(String doctorId, int limit);

    record PendingNotice(
            String orderId,
            String consultationId,
            String doctorId,
            String specialty,
            String scheduleId) {
    }
}
