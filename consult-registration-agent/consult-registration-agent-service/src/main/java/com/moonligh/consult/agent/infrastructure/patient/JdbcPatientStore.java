package com.moonligh.consult.agent.infrastructure.patient;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;

import com.moonligh.consult.agent.api.enums.Gender;
import com.moonligh.consult.agent.domain.patient.PatientRecord;
import com.moonligh.consult.agent.domain.patient.PatientStore;

/**
 * 旧扁平就诊人表。生产改走身份服务，单测仍可用内存实现。
 */
public class JdbcPatientStore implements PatientStore {

    private final JdbcTemplate jdbc;

    public JdbcPatientStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PatientRecord> list(String accountId) {
        return jdbc.query(
                """
                SELECT id, account_id, name, gender, age, relation, is_default
                FROM consult_patient
                WHERE account_id = ? AND is_deleted = 0
                ORDER BY is_default DESC, created_at ASC
                """,
                this::mapRow,
                accountId);
    }

    @Override
    public PatientRecord find(String accountId, String patientId) {
        List<PatientRecord> rows = jdbc.query(
                """
                SELECT id, account_id, name, gender, age, relation, is_default
                FROM consult_patient
                WHERE id = ? AND account_id = ? AND is_deleted = 0
                """,
                this::mapRow,
                patientId,
                accountId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public PatientRecord insert(PatientRecord patient) {
        jdbc.update(
                """
                INSERT INTO consult_patient
                (id, account_id, name, gender, age, relation, is_default, created_at, updated_at, is_deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)
                """,
                patient.id(),
                patient.accountId(),
                patient.name(),
                patient.gender().getCode(),
                patient.age(),
                patient.relation(),
                patient.isDefault() ? 1 : 0);
        return patient;
    }

    @Override
    public boolean isEmpty(String accountId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM consult_patient WHERE account_id = ? AND is_deleted = 0",
                Integer.class,
                accountId);
        return count == null || count == 0;
    }

    private PatientRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new PatientRecord(
                rs.getString("id"),
                rs.getString("account_id"),
                rs.getString("name"),
                Gender.fromCode(rs.getInt("gender")),
                rs.getInt("age"),
                rs.getString("relation"),
                rs.getInt("is_default") == 1);
    }
}
