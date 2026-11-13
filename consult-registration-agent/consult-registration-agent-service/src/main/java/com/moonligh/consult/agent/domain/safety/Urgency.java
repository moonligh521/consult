package com.moonligh.consult.agent.domain.safety;

/**
 * 内部紧急程度。不直接出现在接口里。
 */
public enum Urgency {

    /** 没有急症信号。 */
    NONE,
    /** 需要尽快评估，但不停止挂号。 */
    PROMPT_EVALUATION,
    /** 说不清楚，走安全降级。 */
    UNDETERMINED,
    /** 急症，必须停止继续挂号。 */
    URGENT
}
