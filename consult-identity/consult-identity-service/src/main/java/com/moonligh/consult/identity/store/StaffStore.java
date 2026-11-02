package com.moonligh.consult.identity.store;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Repository;

/**
 * HIS 管理端工作人员。
 */
@Repository
public class StaffStore {

    public record StaffRow(String id, String username, String passwordHash, String name, int status) {
    }

    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

    public StaffStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int count() {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM consult_staff WHERE is_deleted = 0", Integer.class);
        return n == null ? 0 : n;
    }

    public void insert(String username, String rawPassword, String name) {
        jdbc.update(
                """
                INSERT INTO consult_staff (id, username, password_hash, name, status, created_at, updated_at, is_deleted)
                VALUES (?, ?, ?, ?, 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)
                """,
                UUID.randomUUID().toString(),
                username,
                passwords.encode(rawPassword),
                name);
    }

    public StaffRow findByUsername(String username) {
        return one(
                "SELECT id, username, password_hash, name, status FROM consult_staff WHERE username = ? AND is_deleted = 0",
                username);
    }

    public StaffRow findById(String id) {
        return one(
                "SELECT id, username, password_hash, name, status FROM consult_staff WHERE id = ? AND is_deleted = 0",
                id);
    }

    private StaffRow one(String sql, String key) {
        List<StaffRow> rows = jdbc.query(
                sql,
                (rs, rowNum) -> new StaffRow(
                        rs.getString("id"),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("name"),
                        rs.getInt("status")),
                key);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public boolean matches(String raw, String hash) {
        return passwords.matches(raw, hash);
    }
}
