package com.moonligh.consult.agent.domain.safety;

/**
 * 触发本次安全评估的入口。
 */
public enum AuditTrigger {

    /** 患者发了一句病情。 */
    MESSAGE(1),
    /** 改卡片描述。 */
    CARD_EDIT(2),
    /** 确认前发现还有没评估完的原话。 */
    CONFIRM_RECHECK(3);

    /** 存进审计表的数字。 */
    private final int code;

    AuditTrigger(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
