package com.moonligh.consult.identity.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.moonligh.consult.identity.domain.AssignmentNoticeStore;
import com.moonligh.consult.identity.infrastructure.persistence.AssignmentNoticeMapper;

@Repository
public class AssignmentNoticeStoreImpl implements AssignmentNoticeStore {

    private final AssignmentNoticeMapper notices;

    public AssignmentNoticeStoreImpl(AssignmentNoticeMapper notices) {
        this.notices = notices;
    }

    @Override
    public void record(String orderId, String consultationId, String doctorId, String specialty, String scheduleId) {
        insertIgnore(() -> notices.insertOutbox(orderId, consultationId, doctorId, specialty, scheduleId));
        insertIgnore(() -> notices.insertInbox(orderId, consultationId, doctorId, specialty, scheduleId));
    }

    @Override
    public List<PendingNotice> claimPending(int limit) {
        int size = limit <= 0 ? 100 : Math.min(limit, 200);
        notices.releaseStaleClaims();
        String token = UUID.randomUUID().toString();
        notices.claimPending(token, size);
        return notices.listClaimed(token);
    }

    @Override
    public PendingNotice claimOne(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return null;
        }
        notices.releaseStaleClaim(orderId);
        String token = UUID.randomUUID().toString();
        int claimed = notices.claimOne(token, orderId);
        if (claimed == 0) {
            return null;
        }
        List<PendingNotice> rows = notices.findClaimed(orderId, token);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public void markSent(String orderId) {
        notices.markSent(orderId);
    }

    @Override
    public void releaseClaim(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return;
        }
        notices.releaseClaim(orderId);
    }

    @Override
    public List<PendingNotice> inbox(String doctorId, int limit) {
        int size = limit <= 0 ? 50 : Math.min(limit, 200);
        return notices.inbox(doctorId, size);
    }

    private void insertIgnore(Runnable insert) {
        try {
            insert.run();
        } catch (DuplicateKeyException existing) {
            // 同一订单已写入
        }
    }
}
