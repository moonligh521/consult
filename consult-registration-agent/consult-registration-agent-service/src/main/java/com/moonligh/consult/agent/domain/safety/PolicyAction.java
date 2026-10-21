package com.moonligh.consult.agent.domain.safety;

/**
 * 否定、不确定、已缓解、缺上下文时怎么处置。
 */
public enum PolicyAction {

    ESCALATE,
    UNDETERMINED,
    SUPPRESS
}
