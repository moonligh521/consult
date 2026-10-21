package com.moonligh.consult.agent.domain.safety;

import java.util.List;

/**
 * 一次安全评估的结果。锁存位由 SafetyPolicy.merge 写入会话。
 */
public record SafetyAssessment(
        boolean urgent,
        boolean promptEvaluation,
        boolean humanRequested,
        boolean unsuitableForOnline,
        boolean undetermined,
        FallbackReason fallbackReason,
        String templateId,
        List<String> matchedRuleIds,
        List<String> shadowRuleIds,
        List<EvidenceRef> evidence,
        ModelStatus modelStatus) {

    public static SafetyAssessment none(ModelStatus modelStatus) {
        return new SafetyAssessment(
                false, false, false, false, false, null, null,
                List.of(), List.of(), List.of(), modelStatus);
    }

    public static SafetyAssessment engineFailure() {
        return new SafetyAssessment(
                false, false, false, false, true, FallbackReason.RULE_ENGINE_ERROR, "FALLBACK-DEFAULT",
                List.of(), List.of(), List.of(), ModelStatus.SKIPPED);
    }
}
