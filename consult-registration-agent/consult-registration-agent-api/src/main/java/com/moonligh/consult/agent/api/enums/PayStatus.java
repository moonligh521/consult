package com.moonligh.consult.agent.api.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 演示支付状态。1 待支付，2 已支付。当前下单后直接记已支付，不走真实渠道。
 */
public enum PayStatus {

    PENDING(1, "待支付"),
    PAID(2, "已支付");

    /** 对外传递的数字。 */
    private final int code;
    /** 中文说明，给页面展示，不进 JSON。 */
    private final String label;

    PayStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    @JsonValue
    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static PayStatus fromCode(int code) {
        for (PayStatus item : values()) {
            if (item.code == code) {
                return item;
            }
        }
        throw new IllegalArgumentException("未知的支付状态 code: " + code);
    }
}
