package com.moonligh.consult.agent.domain.safety;

/**
 * 本轮是真正评估还是急症钉死后的记录。
 */
public enum EvaluationKind {

    EVALUATED(1),
    PINNED_URGENT(2);

    private final int code;

    EvaluationKind(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
