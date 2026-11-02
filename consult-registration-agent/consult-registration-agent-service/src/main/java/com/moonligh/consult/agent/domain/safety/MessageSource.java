package com.moonligh.consult.agent.domain.safety;

/**
 * 原话来源。1 患者消息；2 改卡描述。
 */
public enum MessageSource {

    /** 患者发来的消息。 */
    MESSAGE(1),
    /** 修改卡片时写入的描述。 */
    CARD_EDIT(2);

    /** 存进数据库的数字。 */
    private final int code;

    MessageSource(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /** 从数据库数字还原来源。 */
    public static MessageSource fromCode(int code) {
        for (MessageSource item : values()) {
            if (item.code == code) {
                return item;
            }
        }
        throw new IllegalArgumentException("未知的原话来源: " + code);
    }
}
