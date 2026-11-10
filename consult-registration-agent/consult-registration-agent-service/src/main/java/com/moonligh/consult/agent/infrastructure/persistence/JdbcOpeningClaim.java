package com.moonligh.consult.agent.infrastructure.persistence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.HexFormat;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.moonligh.consult.agent.domain.OpeningClaim;

/** 开轮占位落在库里，多台挂号服务看见同一行。 */
@Repository
public class JdbcOpeningClaim implements OpeningClaim {

    private final JdbcTemplate jdbc;

    public JdbcOpeningClaim(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String claim(String accountId, String text, String consultationId, Instant now) {
        if (accountId == null || accountId.isBlank() || text == null || text.isBlank() || consultationId == null) {
            return consultationId;
        }
        String account = accountId.trim();
        String hash = sha(text.trim());
        Instant at = now == null ? Instant.now() : now;
        Timestamp stamp = Timestamp.from(at);
        try {
            jdbc.update(
                    """
                    INSERT INTO consult_opening (account_id, text_hash, consultation_id, created_at)
                    VALUES (?, ?, ?, ?)
                    """,
                    account,
                    hash,
                    consultationId,
                    stamp);
            return consultationId;
        } catch (DuplicateKeyException existing) {
            int taken = jdbc.update(
                    """
                    UPDATE consult_opening
                    SET consultation_id = ?, created_at = ?
                    WHERE account_id = ? AND text_hash = ? AND created_at < ?
                    """,
                    consultationId,
                    stamp,
                    account,
                    hash,
                    Timestamp.from(at.minus(WINDOW)));
            if (taken == 1) {
                return consultationId;
            }
            return jdbc.queryForObject(
                    "SELECT consultation_id FROM consult_opening WHERE account_id = ? AND text_hash = ?",
                    String.class,
                    account,
                    hash);
        }
    }

    @Override
    public void replace(String accountId, String text, String consultationId, Instant now) {
        if (accountId == null || accountId.isBlank() || text == null || text.isBlank() || consultationId == null) {
            return;
        }
        String account = accountId.trim();
        String hash = sha(text.trim());
        Timestamp stamp = Timestamp.from(now == null ? Instant.now() : now);
        int updated = jdbc.update(
                """
                UPDATE consult_opening
                SET consultation_id = ?, created_at = ?
                WHERE account_id = ? AND text_hash = ?
                """,
                consultationId,
                stamp,
                account,
                hash);
        if (updated == 0) {
            claim(account, text, consultationId, now);
        }
    }

    private static String sha(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
