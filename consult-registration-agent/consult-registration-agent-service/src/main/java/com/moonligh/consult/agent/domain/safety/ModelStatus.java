package com.moonligh.consult.agent.domain.safety;

/**
 * 本轮模型补漏的执行结果。数字码写入审计表。
 */
public enum ModelStatus {

    DISABLED(0),
    SKIPPED(1),
    SUCCESS(2),
    TIMEOUT(3),
    UNAVAILABLE(4),
    INVALID_OUTPUT(5);

    private final int code;

    ModelStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
