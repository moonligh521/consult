package com.moonligh.consult.agent.domain.safety;

/**
 * 本轮模型补漏的执行结果。数字码写入审计表。
 */
public enum ModelStatus {

    /** 模型没开，不算故障。 */
    DISABLED(0),
    /** 规则已判急症，本轮不调模型。 */
    SKIPPED(1),
    /** 抽到了可用信号。 */
    SUCCESS(2),
    /** 调用超时。 */
    TIMEOUT(3),
    /** 没密钥、限流或上游失败。 */
    UNAVAILABLE(4),
    /** 输出对不上原话或目录。 */
    INVALID_OUTPUT(5);

    /** 存进审计表的数字。 */
    private final int code;

    ModelStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
