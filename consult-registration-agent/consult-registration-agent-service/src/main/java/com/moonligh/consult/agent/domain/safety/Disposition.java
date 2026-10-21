package com.moonligh.consult.agent.domain.safety;

/**
 * 规则命中后的处置。由临床审核确定。
 */
public enum Disposition {

    URGENT,
    PROMPT_EVALUATION,
    PROMPT_EVALUATION_BLOCK_ONLINE,
    HUMAN,
    UNSUITABLE_FOR_ONLINE,
    NO_ESCALATION
}
