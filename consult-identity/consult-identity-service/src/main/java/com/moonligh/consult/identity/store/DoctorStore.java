package com.moonligh.consult.identity.store;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.moonligh.consult.identity.api.DoctorAdminRes;
import com.moonligh.consult.identity.api.DoctorCoverageRes;
import com.moonligh.consult.identity.api.DoctorSaveReq;
import com.moonligh.consult.identity.domain.DoctorMatcher;
import com.moonligh.consult.identity.domain.ScheduleWindows;

/**
 * 医生档案。一级科室、二级科室、擅长分列。
 */
@Repository
public class DoctorStore {

    private final JdbcTemplate jdbc;

    public DoctorStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<String[]> listEnabledSpecialties() {
        try {
            return jdbc.query(
                    """
                    SELECT d.code, s.name
                    FROM consult_department_specialty s
                    JOIN consult_department d ON d.code = s.department_code AND d.is_deleted = 0 AND d.enabled = 1
                    WHERE s.is_deleted = 0 AND s.enabled = 1
                    ORDER BY d.sort_no, s.sort_no, s.id
                    """,
                    (rs, rowNum) -> new String[] {String.valueOf(rs.getInt("code")), rs.getString("name")});
        } catch (RuntimeException error) {
            return List.of();
        }
    }

    public int count() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM consult_doctor WHERE is_deleted = 0", Integer.class);
        return n == null ? 0 : n;
    }

    /** 某二级科在职医生数。 */
    public DoctorCoverageRes coverage(int departmentCode, String specialty) {
        Integer doctors = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM consult_doctor
                WHERE is_deleted = 0 AND status = 1 AND department_code = ? AND specialty = ?
                """,
                Integer.class,
                departmentCode,
                specialty);
        Integer onDuty = jdbc.queryForObject(
                """
                SELECT COUNT(DISTINCT d.id)
                FROM consult_doctor d
                JOIN consult_schedule s ON s.doctor_id = d.id AND s.is_deleted = 0 AND s.stopped = 0
                  AND s.remain_num > 0
                """ + ScheduleWindows.LIVE_SQL + """
                WHERE d.is_deleted = 0 AND d.status = 1 AND d.department_code = ? AND d.specialty = ?
                  AND d.in_flight < d.concurrent_limit
                """,
                Integer.class,
                departmentCode,
                specialty);
        Integer remain = jdbc.queryForObject(
                """
                SELECT COALESCE(SUM(s.remain_num), 0)
                FROM consult_doctor d
                JOIN consult_schedule s ON s.doctor_id = d.id AND s.is_deleted = 0 AND s.stopped = 0
                  AND s.visit_date = CURDATE()
                WHERE d.is_deleted = 0 AND d.status = 1 AND d.department_code = ? AND d.specialty = ?
                """,
                Integer.class,
                departmentCode,
                specialty);
        return new DoctorCoverageRes(
                doctors == null ? 0 : doctors,
                onDuty == null ? 0 : onDuty,
                remain == null ? 0 : remain);
    }

    public int countBySpecialty(String specialty) {
        Integer n = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM consult_doctor
                WHERE is_deleted = 0 AND status = 1 AND specialty = ?
                """,
                Integer.class,
                specialty);
        return n == null ? 0 : n;
    }

    public List<DoctorAdminRes> listBySpecialty(String specialty) {
        return jdbc.query(
                """
                SELECT id, name, gender, title, hospital, department_code, specialty, skills, intro,
                       in_flight, concurrent_limit, status
                FROM consult_doctor
                WHERE is_deleted = 0 AND specialty = ?
                ORDER BY name
                """,
                this::mapAdmin,
                specialty);
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

    /**
     * 派单候选：只取 DoctorMatcher 会看的三层——同二级科、同一级科、全科兜底（一级码 4 或二级「全科」），
     * 不再每单把全院 7 天排班拉回内存。
     */
    public List<DoctorMatcher.Candidate> listOnDuty(String specialty, Integer departmentCode) {
        String scope = """
                  AND (d.specialty = ? OR d.department_code = ? OR d.department_code = 4 OR d.specialty = '全科')
                ORDER BY s.visit_date, s.start_time
                """;
        Object[] args = {specialty == null ? "" : specialty, departmentCode == null ? -1 : departmentCode};
        String sql = """
                SELECT d.id, d.name, d.title, d.hospital, d.department_code, d.specialty, d.skills,
                       d.in_flight, s.id AS schedule_id, s.remain_num
                FROM consult_doctor d
                JOIN consult_department dept
                  ON dept.code = d.department_code AND dept.enabled = 1 AND dept.is_deleted = 0
                JOIN consult_department_specialty spec
                  ON spec.is_deleted = 0 AND spec.enabled = 1
                 AND spec.department_code = d.department_code AND spec.name = d.specialty
                JOIN consult_schedule s ON s.doctor_id = d.id AND s.is_deleted = 0 AND s.stopped = 0
                  AND s.remain_num > 0
                """ + ScheduleWindows.LIVE_SQL + """
                WHERE d.is_deleted = 0 AND d.status = 1 AND d.in_flight < d.concurrent_limit
                """ + scope;
        try {
            return jdbc.query(sql, this::mapCandidate, args);
        } catch (RuntimeException error) {
            return jdbc.query(
                    """
                    SELECT d.id, d.name, d.title, d.hospital, d.department_code, d.specialty, d.skills,
                           d.in_flight, s.id AS schedule_id, s.remain_num
                    FROM consult_doctor d
                    JOIN consult_schedule s ON s.doctor_id = d.id AND s.is_deleted = 0 AND s.stopped = 0
                      AND s.remain_num > 0
                    """ + ScheduleWindows.LIVE_SQL + """
                    WHERE d.is_deleted = 0 AND d.status = 1 AND d.in_flight < d.concurrent_limit
                    """ + scope,
                    this::mapCandidate,
                    args);
        }
    }

    private DoctorMatcher.Candidate mapCandidate(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new DoctorMatcher.Candidate(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("title"),
                rs.getString("hospital"),
                rs.getInt("department_code"),
                rs.getString("specialty"),
                rs.getString("skills"),
                rs.getInt("in_flight"),
                rs.getInt("remain_num"),
                rs.getString("schedule_id"));
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
