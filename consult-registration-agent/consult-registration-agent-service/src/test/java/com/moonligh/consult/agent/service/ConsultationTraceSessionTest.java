package com.moonligh.consult.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.moonligh.consult.agent.api.enums.Gender;
import com.moonligh.consult.agent.api.req.CardPatchReq;
import com.moonligh.consult.agent.api.req.PatientCreateReq;
import com.moonligh.consult.agent.api.res.ConsultationRes;
import com.moonligh.consult.framework.observability.AgentGeneration;
import com.moonligh.consult.framework.observability.AgentSpan;
import com.moonligh.consult.framework.observability.AgentTrace;
import com.moonligh.consult.framework.observability.AgentTraceContext;
import com.moonligh.consult.framework.observability.AgentTraceNames;

/**
 * 预演和锁内共用真实咨询编号；安全决策 span 只在提交那一遍写一次。
 */
class ConsultationTraceSessionTest {

    @Test
    void firstMessageBindsRealConsultationIdOnceForSafetyDecision() {
        CapturingTrace trace = new CapturingTrace();
        ConsultationsFixture fixture = ConsultationsFixture.of(
                ConsultationsFixture.shadow().catalog,
                new com.moonligh.consult.agent.domain.safety.RuleSafetyEngine(),
                new com.moonligh.consult.agent.domain.safety.DisabledSignalExtractor(),
                trace);

        ConsultationRes view = fixture.service.acceptMessage(null, "女，32岁，干咳半年");

        assertNotEquals("draft", view.consultationId());
        assertFalse(trace.consultationIds.contains("draft"));
        assertTrue(trace.consultationIds.contains(view.consultationId()));
        assertEquals(1, trace.safetyDecisions);
        assertNull(trace.current);
    }

    @Test
    void blankMessageClearsTraceBinding() {
        CapturingTrace trace = new CapturingTrace();
        ConsultationsFixture fixture = ConsultationsFixture.of(
                ConsultationsFixture.shadow().catalog,
                new com.moonligh.consult.agent.domain.safety.RuleSafetyEngine(),
                new com.moonligh.consult.agent.domain.safety.DisabledSignalExtractor(),
                trace);

        ConsultationRes view = fixture.service.acceptMessage(null, null, "女，32岁，干咳半年", "acct-1");
        fixture.service.acceptMessage(view.consultationId(), null, "   ", "acct-1");
        assertNull(trace.current);
    }

    @Test
    void cardEditBindsSessionBeforeSafetySpan() {
        CapturingTrace trace = new CapturingTrace();
        ConsultationsFixture fixture = ConsultationsFixture.of(
                ConsultationsFixture.shadow().catalog,
                new com.moonligh.consult.agent.domain.safety.RuleSafetyEngine(),
                new com.moonligh.consult.agent.domain.safety.DisabledSignalExtractor(),
                trace);

        ConsultationRes ready = fixture.service.acceptMessage(null, "女，32岁，干咳半年");
        trace.safetyConsultationIds.clear();
        fixture.service.updateCard(new CardPatchReq(ready.consultationId(), null, null, null, "干咳加重一周", null));

        assertFalse(trace.safetyConsultationIds.isEmpty());
        assertTrue(trace.safetyConsultationIds.stream().allMatch(ready.consultationId()::equals));
        assertNull(trace.current);
    }

    @Test
    void bindPatientStampsSession() {
        CapturingTrace trace = new CapturingTrace();
        ConsultationsFixture fixture = ConsultationsFixture.of(
                ConsultationsFixture.shadow().catalog,
                new com.moonligh.consult.agent.domain.safety.RuleSafetyEngine(),
                new com.moonligh.consult.agent.domain.safety.DisabledSignalExtractor(),
                trace);
        PatientService patients = new PatientService(fixture.patients);
        var self = patients.create("demo", new PatientCreateReq("张明", Gender.MALE, 32, "本人"));

        ConsultationRes first = fixture.service.acceptMessage(null, "干咳半年，运动后加重");
        trace.consultationIds.clear();
        ConsultationRes bound = fixture.service.bindPatient(first.consultationId(), self.id(), "demo");

        assertNotNull(bound.consultationId());
        assertTrue(trace.consultationIds.contains(first.consultationId()));
        assertNull(trace.current);
    }

    @Test
    void multiTurnEachRoundExplainsInputOutputOrWhyNextRound() {
        CapturingTrace trace = new CapturingTrace();
        ConsultationsFixture fixture = ConsultationsFixture.of(
                ConsultationsFixture.shadow().catalog,
                new com.moonligh.consult.agent.domain.safety.RuleSafetyEngine(),
                new com.moonligh.consult.agent.domain.safety.DisabledSignalExtractor(),
                trace);
        PatientService patients = new PatientService(fixture.patients);
        var self = patients.create("demo", new PatientCreateReq("张明", Gender.MALE, 32, "本人"));

        ConsultationRes vague = fixture.service.acceptMessage(null, "有点不舒服");
        assertEquals(1, trace.turns.size());
        assertTurn(
                trace.turns.get(0),
                "有点不舒服",
                "no_card · vague_ask · 有点不舒服",
                "N2_VAGUE",
                false);

        ConsultationRes tooth = fixture.service.acceptMessage(vague.consultationId(), "牙疼");
        assertEquals(2, trace.turns.size());
        assertTurn(
                trace.turns.get(1),
                "牙疼",
                "no_card · ask_patient · 牙疼",
                "N3_SLOT",
                false);

        ConsultationRes bound = fixture.service.bindPatient(tooth.consultationId(), self.id(), "demo");
        assertEquals(3, trace.turns.size());
        assertTurn(
                trace.turns.get(2),
                self.id(),
                "no_card · bind_patient · missing_duration",
                "BIND_PATIENT",
                false);
        assertEquals("DURATION", trace.turns.get(2).get("missing_slot"));
        assertFalse(String.valueOf(trace.turns.get(2)).contains("张明"));

        ConsultationRes card = fixture.service.acceptMessage(bound.consultationId(), "3天");
        assertEquals(4, trace.turns.size());
        Map<String, Object> ready = trace.turns.get(3);
        assertEquals("3天", ready.get("langfuse.observation.input"));
        assertEquals("card · 口腔科 · RULE", ready.get("langfuse.trace.name"));
        assertTrue(String.valueOf(ready.get("langfuse.observation.output")).contains("Card \"口腔科\""));
        assertEquals("N6_DEPARTMENT", ready.get("turn_node"));
        assertEquals(true, ready.get("card_ready"));
        assertEquals("口腔科", card.card().specialty());
        assertNull(trace.current);
    }

    private static void assertTurn(
            Map<String, Object> attrs, String input, String traceName, String node, boolean cardReady) {
        assertEquals(input, attrs.get("langfuse.observation.input"));
        assertEquals(traceName, attrs.get("langfuse.trace.name"));
        assertEquals(node, attrs.get("turn_node"));
        assertEquals(cardReady, attrs.get("card_ready"));
        String output = String.valueOf(attrs.get("langfuse.observation.output"));
        assertTrue(output.contains(cardReady ? "Card \"" : "No card"));
        if (!cardReady) {
            assertTrue(output.contains("prompt:") || output.contains("No card"));
        }
    }

    private static final class CapturingTrace implements AgentTrace {
        private final List<String> consultationIds = new ArrayList<>();
        private final List<String> safetyConsultationIds = new ArrayList<>();
        private final List<Map<String, Object>> turns = new ArrayList<>();
        private int safetyDecisions;
        private AgentTraceContext current;

        @Override
        public AgentSpan span(String name) {
            if (AgentTraceNames.SAFETY.equals(name)) {
                safetyConsultationIds.add(current == null ? null : current.consultationId());
            }
            if ("agent.safety.decision".equals(name)) {
                safetyDecisions++;
            }
            if (AgentTraceNames.TURN.equals(name)) {
                Map<String, Object> attrs = new LinkedHashMap<>();
                turns.add(attrs);
                return capturingSpan(attrs);
            }
            return AgentTrace.NOOP.span(name);
        }

        @Override
        public AgentGeneration generation(String name) {
            return AgentTrace.NOOP.generation(name);
        }

        @Override
        public void bind(AgentTraceContext context) {
            current = context;
            if (context != null) {
                consultationIds.add(context.consultationId());
            }
        }

        @Override
        public void clear() {
            current = null;
        }

        @Override
        public String currentTraceId() {
            return null;
        }

        private static AgentSpan capturingSpan(Map<String, Object> attrs) {
            return new AgentSpan() {
                @Override
                public AgentSpan attr(String key, Object value) {
                    attrs.put(key, value);
                    return this;
                }

                @Override
                public AgentSpan event(String name) {
                    return this;
                }

                @Override
                public void fail(String message) {
                }

                @Override
                public void close() {
                }
            };
        }
    }
}
