package com.moonligh.consult.agent.domain.triage;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.moonligh.consult.agent.domain.safety.ModelStatus;

/**
 * consult_match_decision 的一行：一个节点一次决策。不含原话。
 */
public record MatchDecision(
        String decisionId,
        String runId,
        String consultationId,
        String orderId,
        int messageSeq,
        int triggerType,
        TriageNode node,
        Certainty certainty,
        DecisionSource source,
        String reason,
        Map<String, Object> output,
        List<Span> evidence,
        List<String> shadowIds,
        ModelStatus modelStatus,
        Boolean modelCached,
        Long modelLatencyMs,
        long latencyMs,
        String catalogVersion,
        String promptVersion,
        String modelName,
        String fingerprint,
        Instant decisionTime) {

    public static final int TRIGGER_CARD = 1;
    public static final int TRIGGER_EDIT_DESCRIPTION = 2;
    public static final int TRIGGER_PATIENT_DEPARTMENT = 3;
    public static final int TRIGGER_BIND_PATIENT = 4;
    public static final int TRIGGER_CONFIRM = 5;
    public static final int TRIGGER_ASSIGN = 6;
    public static final int TRIGGER_VAGUE = 7;

    public MatchDecision {
        Map<String, Object> kept = new LinkedHashMap<>();
        if (output != null) {
            output.forEach((key, value) -> {
                if (value != null) {
                    kept.put(key, value);
                }
            });
        }
        output = Collections.unmodifiableMap(kept);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        shadowIds = shadowIds == null ? List.of() : List.copyOf(shadowIds);
    }

    /** 引擎侧先生成，会话上下文由服务层补。 */
    public static MatchDecision of(
            String runId,
            TriageNode node,
            Certainty certainty,
            DecisionSource source,
            String reason,
            Map<String, Object> output,
            List<Span> evidence,
            long latencyMs) {
        return new MatchDecision(UUID.randomUUID().toString(), runId, null, null, 0, 0, node, certainty, source,
                reason, output, evidence, List.of(), null, null, null, latencyMs, null, null, null, null, Instant.now());
    }

    public MatchDecision withContext(String consultation, String order, int seq, int trigger) {
        return new MatchDecision(decisionId, runId, consultation, order, seq, trigger, node, certainty, source, reason,
                output, evidence, shadowIds, modelStatus, modelCached, modelLatencyMs, latencyMs, catalogVersion,
                promptVersion, modelName, fingerprint, decisionTime);
    }

    public MatchDecision withModel(ModelStatus status, Boolean cached, Long latency, String prompt, String model) {
        return new MatchDecision(decisionId, runId, consultationId, orderId, messageSeq, triggerType, node, certainty,
                source, reason, output, evidence, shadowIds, status, cached, latency, latencyMs, catalogVersion,
                prompt, model, fingerprint, decisionTime);
    }

    public MatchDecision withVersion(String catalog, String inputFingerprint, List<String> shadows) {
        return new MatchDecision(decisionId, runId, consultationId, orderId, messageSeq, triggerType, node, certainty,
                source, reason, output, evidence, shadows, modelStatus, modelCached, modelLatencyMs, latencyMs, catalog,
                promptVersion, modelName, inputFingerprint, decisionTime);
    }
}
