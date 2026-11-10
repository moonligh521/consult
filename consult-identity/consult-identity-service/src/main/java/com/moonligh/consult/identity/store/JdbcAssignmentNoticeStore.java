package com.moonligh.consult.identity.store;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.moonligh.consult.identity.domain.AssignmentNoticeStore;

@Repository
public class JdbcAssignmentNoticeStore implements AssignmentNoticeStore {

    private final JdbcTemplate jdbc;

    public JdbcAssignmentNoticeStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(String orderId, String consultationId, String doctorId, String specialty, String scheduleId) {
        insertIgnore(
                """
                INSERT INTO consult_assignment_outbox
                (order_id, consultation_id, doctor_id, specialty, schedule_id, status, created_at)
                VALUES (?, ?, ?, ?, ?, 0, CURRENT_TIMESTAMP(3))
                """,
                orderId, consultationId, doctorId, specialty, scheduleId);
        insertIgnore(
                """
                INSERT INTO consult_doctor_inbox
                (order_id, consultation_id, doctor_id, specialty, schedule_id, created_at)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP(3))
                """,
                orderId, consultationId, doctorId, specialty, scheduleId);
    }

    @Override
    public List<PendingNotice> claimPending(int limit) {
        int size = limit <= 0 ? 100 : Math.min(limit, 200);
        jdbc.update(
                """
                UPDATE consult_assignment_outbox
                SET status = 0, claim_token = NULL, claimed_at = NULL
                WHERE status = 2
                  AND claimed_at < DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 60 SECOND)
                """);
        String token = UUID.randomUUID().toString();
        jdbc.update(
                """
                UPDATE consult_assignment_outbox
                SET status = 2, claim_token = ?, claimed_at = CURRENT_TIMESTAMP(3)
                WHERE status = 0
                ORDER BY created_at
                LIMIT 
                """ + size,
                token);
        return jdbc.query(
                """
                SELECT order_id, consultation_id, doctor_id, specialty, schedule_id
                FROM consult_assignment_outbox
                WHERE status = 2 AND claim_token = ?
                ORDER BY created_at
                """,
                (rs, rowNum) -> new PendingNotice(
                        rs.getString("order_id"),
                        rs.getString("consultation_id"),
                        rs.getString("doctor_id"),
                        rs.getString("specialty"),
                        rs.getString("schedule_id")),
                token);
    }

    @Override
    public PendingNotice claimOne(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return null;
        }
        jdbc.update(
                """
                UPDATE consult_assignment_outbox
                SET status = 0, claim_token = NULL, claimed_at = NULL
                WHERE order_id = ? AND status = 2
                  AND claimed_at < DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 60 SECOND)
                """,
                orderId);
        String token = UUID.randomUUID().toString();
        int claimed = jdbc.update(
                """
                UPDATE consult_assignment_outbox
                SET status = 2, claim_token = ?, claimed_at = CURRENT_TIMESTAMP(3)
                WHERE order_id = ? AND status = 0
                """,
                token,
                orderId);
        if (claimed == 0) {
            return null;
        }
        List<PendingNotice> rows = jdbc.query(
                """
                SELECT order_id, consultation_id, doctor_id, specialty, schedule_id
                FROM consult_assignment_outbox
                WHERE order_id = ? AND status = 2 AND claim_token = ?
                """,
                (rs, rowNum) -> new PendingNotice(
                        rs.getString("order_id"),
                        rs.getString("consultation_id"),
                        rs.getString("doctor_id"),
                        rs.getString("specialty"),
                        rs.getString("schedule_id")),
                orderId,
                token);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public void markSent(String orderId) {
        jdbc.update(
                """
                UPDATE consult_assignment_outbox
                SET status = 1, claim_token = NULL, sent_at = CURRENT_TIMESTAMP(3)
                WHERE order_id = ? AND status <> 1
                """,
                orderId);
    }

    @Override
    public void releaseClaim(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return;
        }
        jdbc.update(
                """
                UPDATE consult_assignment_outbox
                SET status = 0, claim_token = NULL, claimed_at = NULL
                WHERE order_id = ? AND status = 2
                """,
                orderId);
    }

    @Override
    public List<PendingNotice> inbox(String doctorId, int limit) {
        int size = limit <= 0 ? 50 : Math.min(limit, 200);
        return jdbc.query(
                """
                SELECT order_id, consultation_id, doctor_id, specialty, schedule_id
                FROM consult_doctor_inbox
                WHERE doctor_id = ?
                ORDER BY created_at DESC
                LIMIT ?
                """,
                (rs, rowNum) -> new PendingNotice(
                        rs.getString("order_id"),
                        rs.getString("consultation_id"),
                        rs.getString("doctor_id"),
                        rs.getString("specialty"),
                        rs.getString("schedule_id")),
                doctorId,
                size);
    }

    private void insertIgnore(String sql, Object... args) {
        try {
            jdbc.update(sql, args);
        } catch (DuplicateKeyException existing) {
            // 同一订单已写入
        }
    }
}
