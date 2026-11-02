package com.moonligh.consult.agent.domain.safety;

/**
 * 否定、不确定、已缓解、缺上下文时怎么处置。
 */
public enum PolicyAction {

    /** 按规则的处置升级。 */
    ESCALATE,
    /** 说不清楚，走安全降级。 */
    UNDETERMINED,
    /** 这次命中不算。 */
    SUPPRESS
}
