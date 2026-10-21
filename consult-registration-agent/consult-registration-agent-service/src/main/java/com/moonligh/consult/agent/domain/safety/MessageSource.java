package com.moonligh.consult.agent.domain.safety;

/**
 * 原话来源。1 患者消息；2 改卡描述。
 */
public enum MessageSource {

    MESSAGE(1),
    CARD_EDIT(2);

    private final int code;

    MessageSource(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static MessageSource fromCode(int code) {
        for (MessageSource item : values()) {
            if (item.code == code) {
                return item;
            }
        }
        throw new IllegalArgumentException("未知的原话来源: " + code);
    }
}
