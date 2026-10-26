package com.moonligh.consult.agent.domain.triage;

/**
 * 一个节点的结论最终来自哪一层。
 */
public enum DecisionSource {

    RULE(1),
    MODEL(2),
    FALLBACK(3),
    PATIENT(4);

    private final int code;

    DecisionSource(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
