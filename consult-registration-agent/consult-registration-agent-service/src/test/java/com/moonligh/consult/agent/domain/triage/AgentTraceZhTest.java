package com.moonligh.consult.agent.domain.triage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.moonligh.consult.agent.api.enums.ConsultationStatus;
import com.moonligh.consult.agent.api.enums.Department;
import com.moonligh.consult.agent.api.enums.Gender;
import com.moonligh.consult.agent.api.enums.MissingSlot;
import com.moonligh.consult.agent.api.res.ConsultationCardRes;
import com.moonligh.consult.agent.api.res.ConsultationRes;
import com.moonligh.consult.agent.domain.ComplaintClarity;
import com.moonligh.consult.agent.domain.DepartmentRouter;
import com.moonligh.consult.agent.domain.Slots;
import com.moonligh.consult.framework.model.ModelStatus;
import com.moonligh.consult.framework.observability.AgentSpan;

class AgentTraceZhTest {

    @Test
    void triageExplainsRuleSingleSpecialty() {
        Map<String, Object> attrs = new LinkedHashMap<>();
        AgentTraceZh.triage(capturing(attrs), "女,32岁,心血管内科一年", sample(
                DecisionSource.RULE,
                ComplaintCanon.SYMPTOM_ONLY,
                DecisionSource.RULE,
                RouteDecision.SINGLE_SPECIALTY,
                "心血管内科",
                List.of(),
                ModelStatus.SKIPPED));
        assertEquals("心血管内科", attrs.get("specialty"));
        assertEquals("女,32岁,心血管内科一年", attrs.get("complaint"));
        assertEquals("RULE", attrs.get("route_source"));
        assertEquals("SINGLE_SPECIALTY", attrs.get("route_reason"));
        assertEquals("SYMPTOM_ONLY", attrs.get("canon_reason"));
        assertEquals("SKIPPED", attrs.get("model_status"));
        assertEquals(false, attrs.get("fallback"));
        assertEquals("女,32岁,心血管内科一年", attrs.get("langfuse.observation.input"));
        assertEquals("RULE routed to \"心血管内科\", model SKIPPED.", attrs.get("langfuse.observation.output"));
    }

    @Test
    void triageExplainsModelFallback() {
        Map<String, Object> attrs = new LinkedHashMap<>();
        AgentTraceZh.triage(capturing(attrs), "体检报告看不懂三天", sample(
                DecisionSource.MODEL,
                ComplaintCanon.NO_HIT,
                DecisionSource.FALLBACK,
                RouteDecision.NO_HIT,
                "全科",
                List.of("全科"),
                ModelStatus.SUCCESS));
        assertTrue(String.valueOf(attrs.get("langfuse.observation.output")).contains("FALLBACK to \"全科\""));
        assertEquals("MODEL", attrs.get("canon_source"));
        assertEquals("FALLBACK", attrs.get("route_source"));
        assertEquals(true, attrs.get("fallback"));
        assertEquals("SUCCESS", attrs.get("model_status"));
    }

    @Test
    void turnVagueShowsWhyNoCardAndNeedNextRound() {
        ConsultationRes view = new ConsultationRes(
                "c1",
                ConsultationStatus.NEED_DESCRIPTION,
                ComplaintClarity.DETAIL_PROMPT,
                null,
                null,
                null,
                null);
        Map<String, Object> attrs = turnAttrs("N2_VAGUE", "VAGUE_ASK", "有点不舒服", view);
        assertEquals("有点不舒服", attrs.get("langfuse.observation.input"));
        assertEquals("no_card · vague_ask · 有点不舒服", attrs.get("langfuse.trace.name"));
        assertTrue(String.valueOf(attrs.get("langfuse.observation.output")).contains("No card"));
        assertTrue(String.valueOf(attrs.get("langfuse.observation.output")).contains("vague_ask"));
        assertTrue(String.valueOf(attrs.get("langfuse.observation.output")).contains("VAGUE_ASK"));
        assertTrue(String.valueOf(attrs.get("langfuse.observation.output")).contains(ComplaintClarity.DETAIL_PROMPT));
        assertEquals(false, attrs.get("card_ready"));
        assertEquals("N2_VAGUE", attrs.get("turn_node"));
    }

    @Test
    void turnSlotShowsComplaintInputAndAskPatientNext() {
        ConsultationRes view = new ConsultationRes(
                "c1",
                ConsultationStatus.NEED_SLOT,
                Slots.promptFor(MissingSlot.GENDER),
                MissingSlot.GENDER,
                null,
                null,
                null);
        Map<String, Object> attrs = turnAttrs("N3_SLOT", "ASK_GENDER", "牙疼", view);
        assertEquals("牙疼", attrs.get("langfuse.observation.input"));
        assertEquals("no_card · ask_patient · 牙疼", attrs.get("langfuse.trace.name"));
        assertTrue(String.valueOf(attrs.get("langfuse.observation.output")).contains("ask_patient"));
        assertTrue(String.valueOf(attrs.get("langfuse.observation.output")).contains("ASK_GENDER"));
        assertEquals("GENDER", attrs.get("missing_slot"));
        assertEquals(false, attrs.get("card_ready"));
    }

    @Test
    void turnBindShowsWhyDurationStillMissing() {
        ConsultationRes view = new ConsultationRes(
                "c1",
                ConsultationStatus.NEED_SLOT,
                Slots.promptFor(MissingSlot.DURATION),
                MissingSlot.DURATION,
                null,
                null,
                null);
        Map<String, Object> attrs = turnAttrs("BIND_PATIENT", "ASK_DURATION", "绑就诊人", view);
        assertEquals("绑就诊人", attrs.get("langfuse.observation.input"));
        assertEquals("no_card · bind_patient · missing_duration", attrs.get("langfuse.trace.name"));
        assertTrue(String.valueOf(attrs.get("langfuse.observation.output")).contains("ASK_DURATION"));
        assertEquals("DURATION", attrs.get("missing_slot"));
        assertEquals(false, attrs.get("card_ready"));
    }

    @Test
    void turnRedactsMobileFromInput() {
        ConsultationRes view = new ConsultationRes(
                "c1",
                ConsultationStatus.NEED_DESCRIPTION,
                ComplaintClarity.DETAIL_PROMPT,
                null,
                null,
                null,
                null);
        Map<String, Object> attrs = turnAttrs("N2_VAGUE", "VAGUE_ASK", "有点不舒服13800138000", view);
        assertEquals("[redacted]", attrs.get("langfuse.observation.input"));
    }

    @Test
    void turnCardShowsSpecialtyAndRuleSource() {
        ConsultationCardRes card = new ConsultationCardRes(
                Gender.MALE,
                32,
                "3天",
                "牙疼",
                List.of("牙疼"),
                Department.STOMATOLOGY.getCode(),
                "口腔科",
                List.of(),
                null,
                null,
                "牙疼",
                "p1",
                "张明",
                "RULE");
        ConsultationRes view = new ConsultationRes(
                "c1", ConsultationStatus.CARD_READY, "请确认卡片", null, card, null, null);
        Map<String, Object> attrs = turnAttrs("N6_DEPARTMENT", "SLOTS_COMPLETE", "3天", view);
        assertEquals("3天", attrs.get("langfuse.observation.input"));
        assertEquals("card · 口腔科 · RULE", attrs.get("langfuse.trace.name"));
        assertEquals("Card \"口腔科\" · RULE. complaint model skipped.", attrs.get("langfuse.observation.output"));
        assertEquals(true, attrs.get("card_ready"));
        assertEquals("口腔科", attrs.get("specialty"));
    }

    private static Map<String, Object> turnAttrs(String node, String reason, String input, ConsultationRes view) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        AgentTraceZh.turn(capturing(attrs), TurnSnapshot.from(node, reason, input, view));
        return attrs;
    }

    private static TriageResult sample(
            DecisionSource canonSource,
            String canonReason,
            DecisionSource routeSource,
            String routeReason,
            String specialty,
            List<String> candidates,
            ModelStatus modelStatus) {
        CanonResult canon = new CanonResult(
                List.of(), List.of(), List.of(), List.of(),
                Certainty.CERTAIN, canonSource, canonReason, true);
        RouteDecision route = new RouteDecision(
                new DepartmentRouter.Advice(1, "内科", specialty, specialty),
                Certainty.CERTAIN, routeSource, routeReason, candidates, null);
        return new TriageResult("run", "cat", null, null, null, canon, route, modelStatus, 0, false);
    }

    private static AgentSpan capturing(Map<String, Object> attrs) {
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
