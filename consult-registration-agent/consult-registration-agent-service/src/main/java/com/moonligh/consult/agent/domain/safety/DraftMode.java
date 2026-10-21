package com.moonligh.consult.agent.domain.safety;

/**
 * DRAFT 规则是否影响患者决策。生产只允许 SHADOW。
 */
public enum DraftMode {

    SHADOW,
    ENFORCE
}
