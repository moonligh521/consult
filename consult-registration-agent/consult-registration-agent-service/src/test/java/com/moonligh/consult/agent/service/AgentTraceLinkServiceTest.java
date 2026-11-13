package com.moonligh.consult.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

import com.moonligh.consult.agent.api.res.AgentTraceLinkRes;
import com.moonligh.consult.agent.infrastructure.persistence.ConsultationRecords;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.consult.framework.observability.ObservabilityProperties;

class AgentTraceLinkServiceTest {

    @Test
    void buildsSearchUrl() {
        ConsultationRecords mapper = mock(ConsultationRecords.class);
        when(mapper.consultationExists("c1")).thenReturn(true);
        when(mapper.latestMatch("c1")).thenReturn(
                new ConsultationRecords.LatestMatch("run-1", Instant.now()));
        ObservabilityProperties properties = new ObservabilityProperties();
        properties.getLangfuse().setPublicUrl("http://langfuse:3000/");
        properties.getLangfuse().setProjectId("consult-agent");
        AgentTraceLinkRes link = new AgentTraceLinkService(mapper, properties).link("c1");
        assertTrue(link.available());
        assertEquals("run-1", link.runId());
        assertEquals("http://langfuse:3000/project/consult-agent/traces?search=c1", link.url());
    }

    @Test
    void expiredAndMissing() {
        ConsultationRecords mapper = mock(ConsultationRecords.class);
        when(mapper.consultationExists("c1")).thenReturn(true);
        when(mapper.latestMatch("c1")).thenReturn(
                new ConsultationRecords.LatestMatch("run-1", Instant.now().minus(40, ChronoUnit.DAYS)));
        ObservabilityProperties properties = new ObservabilityProperties();
        properties.getLangfuse().setPublicUrl("http://langfuse:3000");
        properties.getLangfuse().setProjectId("consult-agent");
        properties.getLangfuse().setRetentionDays(30);
        AgentTraceLinkRes expired = new AgentTraceLinkService(mapper, properties).link("c1");
        assertFalse(expired.available());
        assertEquals(AgentTraceLinkService.EXPIRED, expired.reason());

        ObservabilityProperties empty = new ObservabilityProperties();
        AgentTraceLinkRes missing = new AgentTraceLinkService(mapper, empty).link("c1");
        assertEquals(AgentTraceLinkService.NOT_CONFIGURED, missing.reason());
    }

    @Test
    void unknownConsultation() {
        ConsultationRecords mapper = mock(ConsultationRecords.class);
        when(mapper.consultationExists("nope")).thenReturn(false);
        BusinessException error = assertThrows(BusinessException.class,
                () -> new AgentTraceLinkService(mapper, new ObservabilityProperties()).link("nope"));
        assertEquals(ErrorCode.CONSULTATION_NOT_FOUND, error.getErrorCode());
    }
}
