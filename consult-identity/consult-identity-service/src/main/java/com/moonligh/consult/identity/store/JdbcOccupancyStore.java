package com.moonligh.consult.identity.store;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.moonligh.consult.identity.domain.OccupancyRow;
import com.moonligh.consult.identity.domain.OccupancyStore;

/**
 * 医生占坑表。同一订单幂等。
 */
@Repository
public class JdbcOccupancyStore implements OccupancyStore {

    private final JdbcTemplate jdbc;

    public JdbcOccupancyStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public OccupancyRow find(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return null;
        }
        List<OccupancyRow> rows = jdbc.query(
                """
                SELECT order_id, doctor_id, schedule_id, status, occupied_at, expires_at, released_at
                FROM consult_doctor_occupancy
                WHERE order_id = ? AND is_deleted = 0
                """,
                this::map,
                orderId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public void insert(OccupancyRow row) {
        jdbc.update(
                """
                INSERT INTO consult_doctor_occupancy
                (order_id, doctor_id, schedule_id, status, occupied_at, expires_at, released_at,
                 created_at, updated_at, is_deleted)
                VALUES (?, ?, ?, ?, ?, ?, NULL, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)
                """,
                row.orderId(),
                row.doctorId(),
                row.scheduleId(),
                row.status(),
                Timestamp.from(row.occupiedAt()),
                Timestamp.from(row.expiresAt()));
    }

    @Override
    public boolean markReleased(String orderId, int reason, Instant at) {
        // status=1 保证两个人同时结束时只有一行能改成功
        return jdbc.update(
                """
                UPDATE consult_doctor_occupancy
                SET status = ?, released_at = ?, updated_at = CURRENT_TIMESTAMP(3)
                WHERE order_id = ? AND status = 1 AND is_deleted = 0
                """,
                reason,
                Timestamp.from(at),
                orderId) == 1;
    }

    @Override
    public boolean updateExpiry(String orderId, Instant expiresAt) {
        return jdbc.update(
                """
                UPDATE consult_doctor_occupancy
                SET expires_at = ?, updated_at = CURRENT_TIMESTAMP(3)
                WHERE order_id = ? AND status = 1 AND is_deleted = 0
                """,
                Timestamp.from(expiresAt),
                orderId) == 1;
    }

    @Override
    public List<OccupancyRow> claimExpired(Instant now, int limit) {
        // SKIP LOCKED：另一台机器正在处理的行直接跳过，不会互相堵住
        return jdbc.query(
                """
                SELECT order_id, doctor_id, schedule_id, status, occupied_at, expires_at, released_at
                FROM consult_doctor_occupancy
                WHERE status = 1 AND is_deleted = 0 AND expires_at <= ?
                ORDER BY expires_at
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """,
                this::map,
                Timestamp.from(now),
                limit);
    }

    private OccupancyRow map(ResultSet rs, int rowNum) throws SQLException {
        Timestamp occupied = rs.getTimestamp("occupied_at");
        Timestamp expires = rs.getTimestamp("expires_at");
        Timestamp released = rs.getTimestamp("released_at");
        return new OccupancyRow(
                rs.getString("order_id"),
                rs.getString("doctor_id"),
                rs.getString("schedule_id"),
                rs.getInt("status"),
                occupied == null ? null : occupied.toInstant(),
                expires == null ? null : expires.toInstant(),
                released == null ? null : released.toInstant());
    }
}
