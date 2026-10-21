package com.moonligh.consult.agent.domain.safety;

/**
 * 触发本次安全评估的入口。
 */
public enum AuditTrigger {

    MESSAGE(1),
    CARD_EDIT(2),
    CONFIRM_RECHECK(3);

    private final int code;

    AuditTrigger(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
