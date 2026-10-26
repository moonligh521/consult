package com.moonligh.consult.agent.domain.safety;

import java.util.List;

/**
 * 一次安全评估的结果。锁存位由 SafetyPolicy.merge 写入会话。
 */
public record SafetyAssessment(
        /** 是否命中急症。 */
        boolean urgent,
        /** 是否需要尽快评估。 */
        boolean promptEvaluation,
        /** 是否要转人工。 */
        boolean humanRequested,
        /** 当前线上渠道是否不适用。 */
        boolean unsuitableForOnline,
        /** 是否说不清楚，要走安全降级。 */
        boolean undetermined,
        /** 降级原因。没降级为 null。 */
        FallbackReason fallbackReason,
        /** 给患者看的提示模板编号。 */
        String templateId,
        /** 真正参与决策的规则编号。 */
        List<String> matchedRuleIds,
        /** 只记影子、不影响患者的规则编号。 */
        List<String> shadowRuleIds,
        /** 命中证据位置，不含原文。 */
        List<EvidenceRef> evidence,
        /** 本轮模型补漏结果。 */
        ModelStatus modelStatus,
        /** 命中的模型触发编号。没触发为空。 */
        List<String> modelTriggerIds,
        /** 规则能否自己定。不能定才调模型。 */
        boolean ruleCertain) {

    public SafetyAssessment {
        modelTriggerIds = modelTriggerIds == null ? List.of() : List.copyOf(modelTriggerIds);
    }

    public SafetyAssessment(
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
        this(urgent, promptEvaluation, humanRequested, unsuitableForOnline, undetermined, fallbackReason,
                templateId, matchedRuleIds, shadowRuleIds, evidence, modelStatus, List.of(), true);
    }

    /** 换掉模型状态和触发信息，其余结论不动。 */
    public SafetyAssessment withModel(ModelStatus status, List<String> triggers, boolean certain) {
        return new SafetyAssessment(urgent, promptEvaluation, humanRequested, unsuitableForOnline, undetermined,
                fallbackReason, templateId, matchedRuleIds, shadowRuleIds, evidence, status, triggers, certain);
    }

    /** 没有命中。急症钉死时也用这个，模型状态记成 SKIPPED。 */
    public static SafetyAssessment none(ModelStatus modelStatus) {
        return new SafetyAssessment(
                false, false, false, false, false, null, null,
                List.of(), List.of(), List.of(), modelStatus);
    }

    /** 规则引擎出错：安全降级。 */
    public static SafetyAssessment engineFailure() {
        return new SafetyAssessment(
                false, false, false, false, true, FallbackReason.RULE_ENGINE_ERROR, "FALLBACK-DEFAULT",
                List.of(), List.of(), List.of(), ModelStatus.SKIPPED);
    }
}
