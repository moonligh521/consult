package com.moonligh.consult.identity.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Repository;

import com.moonligh.consult.identity.infrastructure.persistence.StaffMapper;

/**
 * HIS 管理端工作人员。
 */
@Repository
public class StaffStore {

    public record StaffRow(String id, String username, String passwordHash, String name, int status, String doctorId) {
    }

    private final StaffMapper staff;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

    public StaffStore(StaffMapper staff) {
        this.staff = staff;
    }

    public int count() {
        Integer n = staff.count();
        return n == null ? 0 : n;
    }

    public void insert(String username, String rawPassword, String name) {
        staff.insert(UUID.randomUUID().toString(), username, passwords.encode(rawPassword), name);
    }

    public StaffRow findByUsername(String username) {
        return first(staff.findByUsername(username));
    }

    public StaffRow findById(String id) {
        return first(staff.findById(id));
    }

    public boolean matches(String raw, String hash) {
        return passwords.matches(raw, hash);
    }

    public void bindDoctor(String username, String doctorId) {
        if (username == null || username.isBlank() || doctorId == null || doctorId.isBlank()) {
            return;
        }
        staff.bindDoctor(doctorId.trim(), username.trim());
    }

    private static StaffRow first(List<StaffRow> rows) {
        return rows.isEmpty() ? null : rows.get(0);
    }
}
