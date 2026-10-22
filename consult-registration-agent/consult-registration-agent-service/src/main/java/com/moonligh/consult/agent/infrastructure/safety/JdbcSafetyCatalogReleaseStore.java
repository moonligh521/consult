package com.moonligh.consult.agent.infrastructure.safety;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.moonligh.consult.agent.domain.safety.SafetyCatalog;

/**
 * 启动时把当前急症 JSON 落成不可变发版行。表未建时只打日志，不挡启动。
 */
public class JdbcSafetyCatalogReleaseStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcSafetyCatalogReleaseStore.class);

    private final JdbcTemplate jdbc;

    public JdbcSafetyCatalogReleaseStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void recordClasspath(SafetyCatalog catalog, String classpathLocation) {
        try (InputStream in = JdbcSafetyCatalogReleaseStore.class.getResourceAsStream(classpathLocation)) {
            if (in == null) {
                log.warn("急症目录资源不存在，跳过发版行 {}", classpathLocation);
                return;
            }
            byte[] bytes = in.readAllBytes();
            recordIfAbsent(catalog.catalogVersion(), sha256(bytes), new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception error) {
            log.warn("急症发版行未写入: {}", error.getMessage());
        }
    }

    public void recordIfAbsent(String catalogVersion, String checksum, String payload) {
        try {
            jdbc.update(
                    """
                    INSERT IGNORE INTO consult_safety_catalog_release (catalog_version, checksum, payload)
                    VALUES (?, ?, ?)
                    """,
                    catalogVersion,
                    checksum,
                    payload);
        } catch (DataAccessException error) {
            log.warn("急症发版表不可用，跳过写入: {}", error.getMessage());
        }
    }

    static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 不可用", error);
        }
    }
}
