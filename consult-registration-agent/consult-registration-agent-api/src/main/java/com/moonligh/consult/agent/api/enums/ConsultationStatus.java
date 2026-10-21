package com.moonligh.consult.agent.api.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 咨询状态。接口与数据库都传 code，不传枚举名。
 * 1 待描述；2 待补槽；3 急症分流；4 卡片待确认；5 已确认。
 */
public enum ConsultationStatus {

    NEED_DESCRIPTION(1, "待描述"),
    NEED_SLOT(2, "待补槽"),
    URGENT(3, "急症分流"),
    CARD_READY(4, "卡片待确认"),
    CONFIRMED(5, "已确认");

    private final int code;
    private final String label;

    ConsultationStatus(int code, String label) {
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
    public static ConsultationStatus fromCode(int code) {
        for (ConsultationStatus item : ConsultationStatus.values()) {
            if (item.code == code) {
                return item;
            }
        }
        throw new IllegalArgumentException("未知的状态 code: " + code);
    }
}
