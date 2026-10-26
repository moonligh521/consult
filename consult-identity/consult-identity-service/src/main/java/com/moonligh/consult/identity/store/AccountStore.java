package com.moonligh.consult.identity.store;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.moonligh.consult.agent.api.enums.Gender;
import com.moonligh.consult.identity.domain.AccountRecord;
import com.moonligh.consult.identity.domain.PersonRecord;

/**
 * 账号和就诊人的 MySQL 存取。
 */
@Repository
public class AccountStore {

    private final JdbcTemplate jdbc;

    public AccountStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public AccountRecord findByMobile(String mobile) {
        List<AccountRecord> rows = jdbc.query(
                """
                SELECT id, mobile, status FROM consult_account
                WHERE mobile = ? AND is_deleted = 0
                """,
                (rs, rowNum) -> new AccountRecord(rs.getString("id"), rs.getString("mobile"), rs.getInt("status")),
                mobile);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public AccountRecord insert(String mobile) {
        String id = UUID.randomUUID().toString();
        jdbc.update(
                """
                INSERT INTO consult_account (id, mobile, status, created_at, updated_at, is_deleted)
                VALUES (?, ?, 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)
                """,
                id,
                mobile);
        return new AccountRecord(id, mobile, 1);
    }

    public List<PersonRecord> listPersons(String accountId) {
        return jdbc.query(
                """
                SELECT p.id, b.account_id, p.name, p.gender, p.age, b.relation, b.is_default
                FROM consult_account_patient b
                JOIN consult_person p ON p.id = b.person_id AND p.is_deleted = 0
                WHERE b.account_id = ? AND b.is_deleted = 0
                ORDER BY b.is_default DESC, b.created_at ASC
                """,
                this::mapPerson,
                accountId);
    }

    public PersonRecord findPerson(String accountId, String personId) {
        List<PersonRecord> rows = jdbc.query(
                """
                SELECT p.id, b.account_id, p.name, p.gender, p.age, b.relation, b.is_default
                FROM consult_account_patient b
                JOIN consult_person p ON p.id = b.person_id AND p.is_deleted = 0
                WHERE b.account_id = ? AND b.person_id = ? AND b.is_deleted = 0
                """,
                this::mapPerson,
                accountId,
                personId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public boolean hasPersons(String accountId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM consult_account_patient WHERE account_id = ? AND is_deleted = 0",
                Integer.class,
                accountId);
        return count != null && count > 0;
    }

    public List<AccountAdminRow> listAccounts() {
        return jdbc.query(
                """
                SELECT a.id, a.mobile, a.status,
                       (SELECT COUNT(*) FROM consult_account_patient b
                        WHERE b.account_id = a.id AND b.is_deleted = 0) AS person_count,
                       DATE_FORMAT(a.created_at, '%Y-%m-%d %H:%i:%s') AS created_at
                FROM consult_account a
                WHERE a.is_deleted = 0
                ORDER BY a.created_at DESC
                LIMIT 200
                """,
                (rs, rowNum) -> new AccountAdminRow(
                        rs.getString("id"),
                        rs.getString("mobile"),
                        rs.getInt("status"),
                        rs.getInt("person_count"),
                        rs.getString("created_at")));
    }

    public List<AdminPersonRow> listAllPersons(String accountId) {
        String sql = """
                SELECT p.id, b.account_id, a.mobile, p.name, p.gender, p.age, b.relation, b.is_default
                FROM consult_account_patient b
                JOIN consult_person p ON p.id = b.person_id AND p.is_deleted = 0
                JOIN consult_account a ON a.id = b.account_id AND a.is_deleted = 0
                WHERE b.is_deleted = 0
                """;
        if (accountId != null && !accountId.isBlank()) {
            return jdbc.query(
                    sql + " AND b.account_id = ? ORDER BY b.created_at DESC LIMIT 200",
                    this::mapAdminPerson,
                    accountId.trim());
        }
        return jdbc.query(sql + " ORDER BY b.created_at DESC LIMIT 200", this::mapAdminPerson);
    }

    public PersonRecord insertPerson(PersonRecord person) {
        jdbc.update(
                """
                INSERT INTO consult_person (id, name, gender, age, created_at, updated_at, is_deleted)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)
                """,
                person.id(),
                person.name(),
                person.gender().getCode(),
                person.age());
        jdbc.update(
                """
                INSERT INTO consult_account_patient
                (account_id, person_id, relation, is_default, created_at, updated_at, is_deleted)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)
                """,
                person.accountId(),
                person.id(),
                person.relation(),
                person.isDefault() ? 1 : 0);
        return person;
    }

    private PersonRecord mapPerson(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new PersonRecord(
                rs.getString("id"),
                rs.getString("account_id"),
                rs.getString("name"),
                Gender.fromCode(rs.getInt("gender")),
                rs.getInt("age"),
                rs.getString("relation"),
                rs.getInt("is_default") == 1);
    }

    private AdminPersonRow mapAdminPerson(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new AdminPersonRow(
                rs.getString("id"),
                rs.getString("account_id"),
                rs.getString("mobile"),
                rs.getString("name"),
                rs.getInt("gender"),
                rs.getInt("age"),
                rs.getString("relation"),
                rs.getInt("is_default") == 1);
    }

    public record AccountAdminRow(String id, String mobile, int status, int personCount, String createdAt) {
    }

    public record AdminPersonRow(
            String id,
            String accountId,
            String mobile,
            String name,
            int gender,
            int age,
            String relation,
            boolean isDefault) {
    }
}
