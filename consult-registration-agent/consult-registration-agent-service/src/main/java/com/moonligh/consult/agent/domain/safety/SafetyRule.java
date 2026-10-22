package com.moonligh.consult.agent.domain.safety;

import java.util.List;

/**
 * 目录中的一条规则。加载后不可变。
 */
public record SafetyRule(
        /** 规则编号。 */
        String ruleId,
        /** 这条规则自己的版本。 */
        String ruleVersion,
        /** BASELINE / APPROVED 参与决策，DRAFT 默认只记影子。 */
        RuleStatus status,
        /** 临床分类，给审核看。 */
        String clinicalCategory,
        /** 信号名，模型和目录对这个字段。 */
        String signal,
        /** 原话里要匹配的别名。 */
        List<String> aliases,
        /** 需要年龄或性别时写在这里。 */
        List<String> requiredContext,
        /** 排除条件。当前引擎按策略字段处理。 */
        List<String> exclusionConditions,
        /** 被否定时怎么处置。 */
        PolicyAction negatedPolicy,
        /** 不确定时怎么处置。 */
        PolicyAction uncertaintyPolicy,
        /** 已缓解时怎么处置。 */
        PolicyAction resolvedPolicy,
        /** 缺年龄性别时怎么处置。 */
        PolicyAction missingContextPolicy,
        /** 命中后的最终处置。 */
        Disposition disposition,
        /** 给患者看的提示模板。 */
        String patientMessageTemplateId,
        /** 正例：加载时必须命中。 */
        List<String> positiveCases,
        /** 反例：加载时不能命中。 */
        List<String> negativeCases,
        /** 临床审核人。APPROVED 必填。 */
        String clinicalReviewer,
        /** 审核日期。APPROVED 必填。 */
        String reviewDate,
        /** 证据引用。APPROVED 必填。 */
        String evidenceReference) {
}
