package com.moonligh.consult.agent.domain.safety;

import java.time.Instant;
import java.util.List;

import com.moonligh.consult.framework.model.ModelStatus;

/**
 * 一次安全决策的审计记录。不含原话文本。
 */
public record SafetyDecisionAudit(
        /** 本条审计编号。 */
        String decisionId,
        /** 属于哪一轮咨询。 */
        String consultationId,
        /** 评估到哪一条原话。 */
        int messageSeq,
        /** 从哪个接口触发。 */
        AuditTrigger trigger,
        /** 真评估还是急症钉死。 */
        EvaluationKind evaluation,
        /** 参与决策的规则。 */
        List<String> matchedRuleIds,
        /** 只记影子的规则。 */
        List<String> shadowRuleIds,
        /** 证据位置，不含原文。 */
        List<EvidenceRef> evidence,
        /** 当时用的目录版本。 */
        String catalogVersion,
        /** 当时用的规则引擎版本。 */
        String ruleEngineVersion,
        /** 模型补漏结果。 */
        ModelStatus modelStatus,
        /** 模型名称。没调用时为 null。 */
        String modelVersion,
        /** 降级原因。 */
        FallbackReason fallbackReason,
        /** 评估前对外状态码。 */
        int previousStatus,
        /** 评估后对外状态码。 */
        int resultingStatus,
        /** 评估前业务状态码。 */
        int businessBefore,
        /** 评估后业务状态码。 */
        int businessAfter,
        /** 给患者看的模板编号。 */
        String templateId,
        /** 决策时间。 */
        Instant decisionTime,
        /** 规则能否自己定。 */
        boolean ruleCertain,
        /** 命中的模型触发编号。 */
        List<String> modelTriggerIds) {

    public SafetyDecisionAudit {
        modelTriggerIds = modelTriggerIds == null ? List.of() : List.copyOf(modelTriggerIds);
    }

    public SafetyDecisionAudit(
            String decisionId,
            String consultationId,
            int messageSeq,
            AuditTrigger trigger,
            EvaluationKind evaluation,
            List<String> matchedRuleIds,
            List<String> shadowRuleIds,
            List<EvidenceRef> evidence,
            String catalogVersion,
            String ruleEngineVersion,
            ModelStatus modelStatus,
            String modelVersion,
            FallbackReason fallbackReason,
            int previousStatus,
            int resultingStatus,
            int businessBefore,
            int businessAfter,
            String templateId,
            Instant decisionTime) {
        this(decisionId, consultationId, messageSeq, trigger, evaluation, matchedRuleIds, shadowRuleIds, evidence,
                catalogVersion, ruleEngineVersion, modelStatus, modelVersion, fallbackReason, previousStatus,
                resultingStatus, businessBefore, businessAfter, templateId, decisionTime, true, List.of());
    }
}
