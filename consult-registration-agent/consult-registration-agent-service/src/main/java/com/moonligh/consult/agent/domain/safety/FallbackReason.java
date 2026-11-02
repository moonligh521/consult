package com.moonligh.consult.agent.domain.safety;

/**
 * 无法完成安全判断的原因。
 */
public enum FallbackReason {

    /** 模型超时或调不通。 */
    MODEL_UNAVAILABLE,
    /** 模型输出对不上原话或目录。 */
    MODEL_INVALID_OUTPUT,
    /** 规则引擎自己出错。 */
    RULE_ENGINE_ERROR
}
