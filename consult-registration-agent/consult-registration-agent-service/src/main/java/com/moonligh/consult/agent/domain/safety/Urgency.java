package com.moonligh.consult.agent.domain.safety;

/**
 * 内部紧急程度。不直接出现在接口里。
 */
public enum Urgency {

    NONE,
    PROMPT_EVALUATION,
    UNDETERMINED,
    URGENT
}
