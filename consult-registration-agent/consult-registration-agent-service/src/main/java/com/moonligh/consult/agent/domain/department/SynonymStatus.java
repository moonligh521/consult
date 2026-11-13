package com.moonligh.consult.agent.domain.department;

/**
 * 同义归约规则状态。只有 REVIEWED 参与挂科，DRAFT 只记影子。
 */
public enum SynonymStatus {

    DRAFT(1),
    REVIEWED(2),
    RETIRED(3);

    private final int code;

    SynonymStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static SynonymStatus fromCode(int code) {
        for (SynonymStatus item : values()) {
            if (item.code == code) {
                return item;
            }
        }
        throw new IllegalArgumentException("未知同义规则状态: " + code);
    }
}
