package com.moonligh.consult.agent.domain.safety;

/**
 * 无法完成安全判断的原因。
 */
public enum FallbackReason {

    MODEL_UNAVAILABLE,
    MODEL_INVALID_OUTPUT,
    RULE_ENGINE_ERROR
}
