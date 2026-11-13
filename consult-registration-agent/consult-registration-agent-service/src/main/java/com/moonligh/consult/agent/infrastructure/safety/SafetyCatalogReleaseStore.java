package com.moonligh.consult.agent.infrastructure.safety;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

import com.moonligh.consult.agent.domain.safety.SafetyCatalog;
import com.moonligh.consult.agent.infrastructure.persistence.SafetyCatalogReleaseMapper;

/**
 * 启动时把当前急症 JSON 落成不可变发版行。表未建时只打日志，不挡启动。
 */
public class SafetyCatalogReleaseStore {

    private static final Logger log = LoggerFactory.getLogger(SafetyCatalogReleaseStore.class);

    private final SafetyCatalogReleaseMapper releases;

    public SafetyCatalogReleaseStore(SafetyCatalogReleaseMapper releases) {
        this.releases = releases;
    }

    public void recordClasspath(SafetyCatalog catalog, String classpathLocation) {
        try (InputStream in = SafetyCatalogReleaseStore.class.getResourceAsStream(classpathLocation)) {
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
            releases.insertIgnore(catalogVersion, checksum, payload);
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
