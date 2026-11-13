package com.moonligh.consult.agent.domain.safety;

/**
 * DRAFT 规则是否影响患者决策。生产只允许 SHADOW。
 */
public enum DraftMode {

    /** 命中只记影子，不改患者状态。 */
    SHADOW,
    /** 测试用：DRAFT 也参与决策。生产禁止。 */
    ENFORCE
}
