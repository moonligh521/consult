package com.moonligh.consult.agent.infrastructure.persistence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;

import com.moonligh.consult.agent.domain.OpeningClaim;

/** 开轮占位落在库里，多台挂号服务看见同一行。 */
@Repository
public class OpeningClaimImpl implements OpeningClaim {

    private final OpeningMapper openings;

    public OpeningClaimImpl(OpeningMapper openings) {
        this.openings = openings;
    }

    @Override
    public String claim(String accountId, String text, String consultationId, Instant now) {
        if (accountId == null || accountId.isBlank() || text == null || text.isBlank() || consultationId == null) {
            return consultationId;
        }
        String account = accountId.trim();
        String hash = sha(text.trim());
        Instant at = now == null ? Instant.now() : now;
        try {
            openings.insert(account, hash, consultationId, at);
            return consultationId;
        } catch (DuplicateKeyException existing) {
            int taken = openings.takeIfExpired(consultationId, at, account, hash, at.minus(WINDOW));
            if (taken == 1) {
                return consultationId;
            }
            String holder = openings.findConsultationId(account, hash);
            if (holder == null) {
                throw new EmptyResultDataAccessException(1);
            }
            return holder;
        }
    }

    @Override
    public void replace(String accountId, String text, String consultationId, Instant now) {
        if (accountId == null || accountId.isBlank() || text == null || text.isBlank() || consultationId == null) {
            return;
        }
        String account = accountId.trim();
        String hash = sha(text.trim());
        Instant at = now == null ? Instant.now() : now;
        int updated = openings.replace(consultationId, at, account, hash);
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
