package com.moonligh.consult.agent.domain.triage;

/**
 * 规则层能不能自己定。不能定才交模型。
 */
public enum Certainty {

    CERTAIN(1),
    UNCERTAIN(2);

    private final int code;

    Certainty(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
