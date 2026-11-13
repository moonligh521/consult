package com.moonligh.consult.agent.infrastructure.doctor;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestClient;

import com.moonligh.consult.agent.domain.SpecialtyPresence;
import com.moonligh.consult.framework.Result;
import com.moonligh.consult.security.AuthTokens;
import com.moonligh.consult.security.ConsultAuthProperties;

/**
 * 向身份服务要当前有在线医生的二级科。两秒内复用同一份名单。
 */
@Repository
public class HttpSpecialtyPresence implements SpecialtyPresence {

    private static final Logger log = LoggerFactory.getLogger(HttpSpecialtyPresence.class);
    private static final long FRESH_MS = 2_000;

    private final RestClient client;
    private final ConsultAuthProperties auth;
    private volatile Set<String> names = Set.of();
    private volatile long fetchedAt;

    public HttpSpecialtyPresence(RestClient identityRestClient, ConsultAuthProperties auth) {
        this.client = identityRestClient;
        this.auth = auth;
    }

    @Override
    public boolean online(String specialty) {
        if (specialty == null || specialty.isBlank()) {
            return false;
        }
        try {
            return current().contains(specialty);
        } catch (RuntimeException error) {
            log.warn("在线科室查询失败，暂按原推荐: {}", error.getClass().getSimpleName());
            return true;
        }
    }

    private Set<String> current() {
        long now = System.currentTimeMillis();
        Set<String> snapshot = names;
        if (now - fetchedAt < FRESH_MS && fetchedAt > 0) {
            return snapshot;
        }
        synchronized (this) {
            now = System.currentTimeMillis();
            if (now - fetchedAt < FRESH_MS && fetchedAt > 0) {
                return names;
            }
            Result<List<String>> body = client.get()
                    .uri("/internal/doctors/online-specialties")
                    .header(AuthTokens.INTERNAL_HEADER, auth.getInternalToken())
                    .retrieve()
                    .body(new ParameterizedTypeReference<Result<List<String>>>() {});
            List<String> rows = body == null || body.getData() == null ? List.of() : body.getData();
            names = Set.copyOf(rows);
            fetchedAt = now;
            return names;
        }
    }
}
