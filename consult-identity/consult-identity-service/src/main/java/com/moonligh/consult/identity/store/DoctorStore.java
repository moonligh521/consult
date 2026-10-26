package com.moonligh.consult.identity.store;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.moonligh.consult.identity.api.DoctorAdminRes;
import com.moonligh.consult.identity.api.DoctorSaveReq;
import com.moonligh.consult.identity.domain.DoctorMatcher;

/**
 * 医生档案。一级科室、二级科室、擅长分列。
 */
@Repository
public class DoctorStore {

    private final JdbcTemplate jdbc;

    public DoctorStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int count() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM consult_doctor WHERE is_deleted = 0", Integer.class);
        return n == null ? 0 : n;
    }

    public boolean exists(String id) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM consult_doctor WHERE id = ? AND is_deleted = 0", Integer.class, id);
        return n != null && n > 0;
    }

    public List<DoctorAdminRes> list() {
        return jdbc.query(
                """
                SELECT id, name, gender, title, hospital, department_code, specialty, skills, intro,
                       in_flight, concurrent_limit, status
                FROM consult_doctor
                WHERE is_deleted = 0
                ORDER BY department_code, name
                """,
                this::mapAdmin);
    }

    public DoctorAdminRes find(String id) {
        List<DoctorAdminRes> rows = jdbc.query(
                """
                SELECT id, name, gender, title, hospital, department_code, specialty, skills, intro,
                       in_flight, concurrent_limit, status
                FROM consult_doctor
                WHERE id = ? AND is_deleted = 0
                """,
                this::mapAdmin,
                id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public DoctorAdminRes insert(DoctorSaveReq req) {
        String id = UUID.randomUUID().toString();
        jdbc.update(
                """
                INSERT INTO consult_doctor
                (id, name, gender, title, hospital, department_code, specialty, skills, intro,
                 in_flight, concurrent_limit, status, created_at, updated_at, is_deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 3, ?, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)
                """,
                id,
                req.name().trim(),
                req.gender(),
                req.title().trim(),
                req.hospital().trim(),
                req.departmentCode(),
                req.specialty().trim(),
                blankToNull(req.skills()),
                blankToNull(req.intro()),
                req.status() == null ? 1 : req.status());
        return find(id);
    }

    public void seed(
            String id,
            String name,
            int gender,
            String title,
            String hospital,
            int departmentCode,
            String specialty,
            String skills,
            String intro) {
        jdbc.update(
                """
                INSERT INTO consult_doctor
                (id, name, gender, title, hospital, department_code, specialty, skills, intro,
                 in_flight, concurrent_limit, status, created_at, updated_at, is_deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 3, 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)
                """,
                id, name, gender, title, hospital, departmentCode, specialty, skills, intro);
    }

    public DoctorAdminRes update(String id, DoctorSaveReq req) {
        jdbc.update(
                """
                UPDATE consult_doctor SET
                  name = ?, gender = ?, title = ?, hospital = ?, department_code = ?,
                  specialty = ?, skills = ?, intro = ?, status = ?, updated_at = CURRENT_TIMESTAMP(3)
                WHERE id = ? AND is_deleted = 0
                """,
                req.name().trim(),
                req.gender(),
                req.title().trim(),
                req.hospital().trim(),
                req.departmentCode(),
                req.specialty().trim(),
                blankToNull(req.skills()),
                blankToNull(req.intro()),
                req.status() == null ? 1 : req.status(),
                id);
        return find(id);
    }

    public void backfillSkills() {
        jdbc.update(
                """
                UPDATE consult_doctor SET skills = intro, updated_at = CURRENT_TIMESTAMP(3)
                WHERE is_deleted = 0 AND (skills IS NULL OR skills = '') AND intro IS NOT NULL AND intro <> ''
                """);
    }

    public List<DoctorMatcher.Candidate> listOnDuty() {
        return jdbc.query(
                """
                SELECT d.id, d.name, d.title, d.hospital, d.department_code, d.specialty, d.skills,
                       d.in_flight, s.id AS schedule_id, s.remain_num
                FROM consult_doctor d
                JOIN consult_schedule s ON s.doctor_id = d.id AND s.is_deleted = 0 AND s.stopped = 0
                  AND s.remain_num > 0
                  AND s.visit_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 6 DAY)
                WHERE d.is_deleted = 0 AND d.status = 1 AND d.in_flight < d.concurrent_limit
                ORDER BY s.visit_date, s.start_time
                """,
                (rs, rowNum) -> new DoctorMatcher.Candidate(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("title"),
                        rs.getString("hospital"),
                        rs.getInt("department_code"),
                        rs.getString("specialty"),
                        rs.getString("skills"),
                        rs.getInt("in_flight"),
                        rs.getInt("remain_num"),
                        rs.getString("schedule_id")));
    }

    public boolean occupy(String doctorId, String scheduleId) {
        int doctor = jdbc.update(
                """
                UPDATE consult_doctor SET in_flight = in_flight + 1, updated_at = CURRENT_TIMESTAMP(3)
                WHERE id = ? AND is_deleted = 0 AND status = 1 AND in_flight < concurrent_limit
                """,
                doctorId);
        if (doctor != 1) {
            return false;
        }
        int slot = jdbc.update(
                """
                UPDATE consult_schedule SET remain_num = remain_num - 1, updated_at = CURRENT_TIMESTAMP(3)
                WHERE id = ? AND is_deleted = 0 AND stopped = 0 AND remain_num > 0
                """,
                scheduleId);
        if (slot != 1) {
            jdbc.update(
                    """
                    UPDATE consult_doctor SET in_flight = in_flight - 1, updated_at = CURRENT_TIMESTAMP(3)
                    WHERE id = ? AND in_flight > 0
                    """,
                    doctorId);
            return false;
        }
        return true;
    }

    /** 问诊结束或超时释放图文坑。remain_num 不退，号源已用。 */
    public void releaseFlight(String doctorId) {
        if (doctorId == null || doctorId.isBlank()) {
            return;
        }
        jdbc.update(
                """
                UPDATE consult_doctor SET in_flight = in_flight - 1, updated_at = CURRENT_TIMESTAMP(3)
                WHERE id = ? AND in_flight > 0
                """,
                doctorId);
    }

    private DoctorAdminRes mapAdmin(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new DoctorAdminRes(
                rs.getString("id"),
                rs.getString("name"),
                rs.getInt("gender"),
                rs.getString("title"),
                rs.getString("hospital"),
                rs.getInt("department_code"),
                rs.getString("specialty"),
                rs.getString("skills"),
                rs.getString("intro"),
                rs.getInt("in_flight"),
                rs.getInt("concurrent_limit"),
                rs.getInt("status"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
