package com.moonligh.consult.agent.domain.safety;

/**
 * 目录规则状态。
 */
public enum RuleStatus {

    /** 基线规则，参与决策。 */
    BASELINE,
    /** 已临床审核，参与决策。 */
    APPROVED,
    /** 草稿。生产只记影子。 */
    DRAFT,
    /** 已下线，加载时跳过。 */
    RETIRED
}
