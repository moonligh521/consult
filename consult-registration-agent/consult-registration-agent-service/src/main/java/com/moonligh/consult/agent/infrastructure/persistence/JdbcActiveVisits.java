package com.moonligh.consult.agent.infrastructure.persistence;

import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.moonligh.consult.agent.domain.ActiveVisits;

/**
 * 进行中的问诊落在主键 (account_id, patient_id) 上。两个实例同时支付，只有一行能插进去。
 */
@Repository
public class JdbcActiveVisits implements ActiveVisits {

    private final JdbcTemplate jdbc;

    public JdbcActiveVisits(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean busy(String accountId, String patientId, String consultationId) {
        String holder = find(accountId, patientId);
        return holder != null && !holder.equals(consultationId);
    }

    @Override
    public boolean claim(String accountId, String patientId, String consultationId) {
        try {
            jdbc.update(
                    """
                    INSERT INTO consult_active_visit (account_id, patient_id, consultation_id, created_at)
                    VALUES (?, ?, ?, CURRENT_TIMESTAMP(3))
                    """,
                    accountId,
                    patientId,
                    consultationId);
            return true;
        } catch (DuplicateKeyException existing) {
            return consultationId.equals(find(accountId, patientId));
        }
    }

    @Override
    public void release(String accountId, String patientId, String consultationId) {
        jdbc.update(
                """
                DELETE FROM consult_active_visit
                WHERE account_id = ? AND patient_id = ? AND consultation_id = ?
                """,
                accountId,
                patientId,
                consultationId);
    }

    private String find(String accountId, String patientId) {
        List<String> rows = jdbc.query(
                """
                SELECT consultation_id FROM consult_active_visit
                WHERE account_id = ? AND patient_id = ?
                """,
                (rs, rowNum) -> rs.getString("consultation_id"),
                accountId,
                patientId);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
