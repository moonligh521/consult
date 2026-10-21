package com.moonligh.consult.agent.domain.safety;

import java.time.Instant;
import java.util.List;

/**
 * 一次安全决策的审计记录。不含原话文本。
 */
public record SafetyDecisionAudit(
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
}
