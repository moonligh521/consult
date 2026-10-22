package com.moonligh.consult.agent.domain.safety;

/**
 * 本轮是真正评估还是急症钉死后的记录。
 */
public enum EvaluationKind {

    /** 跑了规则引擎（必要时还有模型）。 */
    EVALUATED(1),
    /** 已经急症，本轮不再跑引擎。 */
    PINNED_URGENT(2);

    /** 存进审计表的数字。 */
    private final int code;

    EvaluationKind(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
