package com.moonligh.consult.agent.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Service;

import com.moonligh.consult.agent.api.res.AgentTraceLinkRes;
import com.moonligh.consult.agent.infrastructure.persistence.ConsultationRecords;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.consult.framework.observability.ObservabilityProperties;

/**
 * 管理端按咨询编号拼 Langfuse 深链。不复制整套链路 UI。
 */
@Service
public class AgentTraceLinkService {

    public static final String NOT_CONFIGURED = "NOT_CONFIGURED";
    public static final String NO_DECISION = "NO_DECISION";
    public static final String EXPIRED = "EXPIRED";

    private final ConsultationRecords mapper;
    private final ObservabilityProperties properties;

    public AgentTraceLinkService(ConsultationRecords mapper, ObservabilityProperties properties) {
        this.mapper = mapper;
        this.properties = properties;
    }

    public AgentTraceLinkRes link(String consultationId) {
        if (consultationId == null || consultationId.isBlank() || !mapper.consultationExists(consultationId)) {
            throw new BusinessException(ErrorCode.CONSULTATION_NOT_FOUND);
        }
        ConsultationRecords.LatestMatch match = mapper.latestMatch(consultationId);
        String runId = match == null ? null : match.runId();
        ObservabilityProperties.Langfuse langfuse = properties.getLangfuse();
        if (langfuse.getPublicUrl() == null || langfuse.getPublicUrl().isBlank()
                || langfuse.getProjectId() == null || langfuse.getProjectId().isBlank()) {
            return AgentTraceLinkRes.missing(NOT_CONFIGURED, consultationId, runId);
        }
        if (match == null) {
            return AgentTraceLinkRes.missing(NO_DECISION, consultationId, null);
        }
        int retention = Math.max(1, langfuse.getRetentionDays());
        if (match.decisionTime().isBefore(Instant.now().minus(retention, ChronoUnit.DAYS))) {
            return AgentTraceLinkRes.missing(EXPIRED, consultationId, runId);
        }
        String query = URLEncoder.encode(consultationId, StandardCharsets.UTF_8);
        String url = trimSlash(langfuse.getPublicUrl())
                + "/project/" + langfuse.getProjectId()
                + "/traces?search=" + query;
        return new AgentTraceLinkRes(true, null, url, consultationId, runId);
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
