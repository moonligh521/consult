package com.moonligh.consult.identity.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.moonligh.consult.agent.api.event.OrderAssignedEvent;
import com.moonligh.consult.framework.mq.ConsultMqSender;
import com.moonligh.consult.framework.mq.ConsultMqTopics;
import com.moonligh.consult.identity.domain.AssignmentNoticeStore;
import com.moonligh.consult.identity.domain.AssignmentNoticeStore.PendingNotice;

/**
 * 事务提交后再投递。发送超时留下 status=0，下一拍重投；医生待接箱已经有唯一一行。
 */
@Service
public class AssignmentPublisher {

    private static final Logger log = LoggerFactory.getLogger(AssignmentPublisher.class);

    private final AssignmentNoticeStore notices;
    private final ConsultMqSender sender;

    public AssignmentPublisher(AssignmentNoticeStore notices, ObjectProvider<ConsultMqSender> sender) {
        this.notices = notices;
        this.sender = sender.getIfAvailable();
    }

    public void publishAfterCommit(String orderId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            sendOne(orderId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sendOne(orderId);
            }
        });
    }

    /** 请求线程只投这一单。失败留下待发，由 assignmentOutboxJob 补投，不能把已提交的派单打成失败。 */
    private void sendOne(String orderId) {
        try {
            if (sender == null) {
                return;
            }
            PendingNotice row = notices.claimOne(orderId);
            if (row == null) {
                return;
            }
            try {
                sender.syncSendJson(
                        ConsultMqTopics.orderAssigned(),
                        new OrderAssignedEvent(
                                row.orderId(),
                                row.consultationId(),
                                row.doctorId(),
                                row.specialty(),
                                row.scheduleId()),
                        row.orderId());
                notices.markSent(row.orderId());
            } catch (RuntimeException error) {
                notices.releaseClaim(row.orderId());
                log.warn("派单通知待重投 orderId={}", row.orderId());
            }
        } catch (RuntimeException error) {
            log.warn("派单通知即时投递失败，等待补投 orderId={} type={}", orderId, error.getClass().getSimpleName());
        }
    }

    public int drain() {
        if (sender == null) {
            return 0;
        }
        int sent = 0;
        List<PendingNotice> batch = notices.claimPending(100);
        for (int i = 0; i < batch.size(); i++) {
            PendingNotice row = batch.get(i);
            try {
                sender.syncSendJson(
                        ConsultMqTopics.orderAssigned(),
                        new OrderAssignedEvent(
                                row.orderId(),
                                row.consultationId(),
                                row.doctorId(),
                                row.specialty(),
                                row.scheduleId()),
                        row.orderId());
                notices.markSent(row.orderId());
                sent++;
            } catch (RuntimeException error) {
                notices.releaseClaim(row.orderId());
                for (int rest = i + 1; rest < batch.size(); rest++) {
                    notices.releaseClaim(batch.get(rest).orderId());
                }
                log.warn("派单通知待重投 orderId={}", row.orderId());
                return sent;
            }
        }
        return sent;
    }
}
