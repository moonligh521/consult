package com.moonligh.consult.identity.store;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.consult.identity.api.ScheduleAdminRes;
import com.moonligh.consult.identity.api.ScheduleSaveReq;

/**
 * 出诊排班。
 */
@Repository
public class ScheduleStore {

    private final JdbcTemplate jdbc;

    public ScheduleStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ScheduleAdminRes> list(String doctorId, String from, String to) {
        String sql = """
                SELECT s.id, s.doctor_id, d.name AS doctor_name, d.specialty, s.visit_date, s.weekday,
                       s.period, s.start_time, s.end_time, s.visit_type, s.limit_num, s.remain_num, s.stopped
                FROM consult_schedule s
                JOIN consult_doctor d ON d.id = s.doctor_id AND d.is_deleted = 0
                WHERE s.is_deleted = 0
                """;
        if (doctorId != null && !doctorId.isBlank() && from != null && !from.isBlank() && to != null && !to.isBlank()) {
            return jdbc.query(
                    sql + " AND s.doctor_id = ? AND s.visit_date BETWEEN ? AND ? ORDER BY s.visit_date, s.start_time",
                    this::map,
                    doctorId.trim(),
                    from,
                    to);
        }
        if (doctorId != null && !doctorId.isBlank()) {
            return jdbc.query(sql + " AND s.doctor_id = ? ORDER BY s.visit_date, s.start_time LIMIT 200", this::map, doctorId.trim());
        }
        if (from != null && !from.isBlank() && to != null && !to.isBlank()) {
            return jdbc.query(
                    sql + " AND s.visit_date BETWEEN ? AND ? ORDER BY s.visit_date, s.start_time LIMIT 300",
                    this::map,
                    from,
                    to);
        }
        return jdbc.query(sql + " AND s.visit_date >= CURDATE() ORDER BY s.visit_date, s.start_time LIMIT 200", this::map);
    }

    public ScheduleAdminRes find(String id) {
        List<ScheduleAdminRes> rows = jdbc.query(
                """
                SELECT s.id, s.doctor_id, d.name AS doctor_name, d.specialty, s.visit_date, s.weekday,
                       s.period, s.start_time, s.end_time, s.visit_type, s.limit_num, s.remain_num, s.stopped
                FROM consult_schedule s
                JOIN consult_doctor d ON d.id = s.doctor_id AND d.is_deleted = 0
                WHERE s.id = ? AND s.is_deleted = 0
                """,
                this::map,
                id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public ScheduleAdminRes insert(ScheduleSaveReq req, LocalDate date, int weekday) {
        String id = UUID.randomUUID().toString();
        int limit = req.limitNum() == null || req.limitNum() < 1 ? 20 : req.limitNum();
        try {
            jdbc.update(
                    """
                    INSERT INTO consult_schedule
                    (id, doctor_id, visit_date, weekday, period, start_time, end_time, visit_type,
                     limit_num, remain_num, stopped, created_at, updated_at, is_deleted)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)
                    """,
                    id,
                    req.doctorId(),
                    date,
                    weekday,
                    req.period(),
                    req.startTime(),
                    req.endTime(),
                    req.visitType() == null || req.visitType().isBlank() ? "图文" : req.visitType().trim(),
                    limit,
                    limit);
        } catch (DuplicateKeyException error) {
            throw new BusinessException(ErrorCode.SCHEDULE_CONFLICT);
        }
        return find(id);
    }

    public void stop(String id, boolean stopped) {
        jdbc.update(
                "UPDATE consult_schedule SET stopped = ?, updated_at = CURRENT_TIMESTAMP(3) WHERE id = ? AND is_deleted = 0",
                stopped ? 1 : 0,
                id);
    }

    public void delete(String id) {
        jdbc.update(
                "UPDATE consult_schedule SET is_deleted = UNIX_TIMESTAMP(), updated_at = CURRENT_TIMESTAMP(3) WHERE id = ? AND is_deleted = 0",
                id);
    }

    private ScheduleAdminRes map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        java.sql.Date visit = rs.getDate("visit_date");
        return new ScheduleAdminRes(
                rs.getString("id"),
                rs.getString("doctor_id"),
                rs.getString("doctor_name"),
                rs.getString("specialty"),
                visit == null ? null : visit.toLocalDate().toString(),
                rs.getInt("weekday"),
                rs.getString("period"),
                rs.getString("start_time"),
                rs.getString("end_time"),
                rs.getString("visit_type"),
                rs.getInt("limit_num"),
                rs.getInt("remain_num"),
                rs.getInt("stopped") == 1);
    }
}
