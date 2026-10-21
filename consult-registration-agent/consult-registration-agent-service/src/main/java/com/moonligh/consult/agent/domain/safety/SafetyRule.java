package com.moonligh.consult.agent.domain.safety;

import java.util.List;

/**
 * 目录中的一条规则。加载后不可变。
 */
public record SafetyRule(
        String ruleId,
        String ruleVersion,
        RuleStatus status,
        String clinicalCategory,
        String signal,
        List<String> aliases,
        List<String> requiredContext,
        List<String> exclusionConditions,
        PolicyAction negatedPolicy,
        PolicyAction uncertaintyPolicy,
        PolicyAction resolvedPolicy,
        PolicyAction missingContextPolicy,
        Disposition disposition,
        String patientMessageTemplateId,
        List<String> positiveCases,
        List<String> negativeCases,
        String clinicalReviewer,
        String reviewDate,
        String evidenceReference) {
}
