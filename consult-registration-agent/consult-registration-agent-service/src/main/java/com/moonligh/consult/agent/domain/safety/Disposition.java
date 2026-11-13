package com.moonligh.consult.agent.domain.safety;

/**
 * 规则命中后的处置。由临床审核确定。
 */
public enum Disposition {

    /** 急症分流。 */
    URGENT,
    /** 尽快评估，不停止挂号。 */
    PROMPT_EVALUATION,
    /** 尽快评估，同时线上渠道不适用。 */
    PROMPT_EVALUATION_BLOCK_ONLINE,
    /** 转人工。 */
    HUMAN,
    /** 当前线上渠道不适用。 */
    UNSUITABLE_FOR_ONLINE,
    /** 命中了但不升级。 */
    NO_ESCALATION
}
