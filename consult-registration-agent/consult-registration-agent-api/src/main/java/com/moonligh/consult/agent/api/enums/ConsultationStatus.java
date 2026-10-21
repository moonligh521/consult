package com.moonligh.consult.agent.api.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 咨询状态。接口与数据库都传 code，不传枚举名。
 * 1 待描述；2 待补槽；3 急症分流；4 卡片待确认；5 已确认；6 需要人工协助。
 */
public enum ConsultationStatus {

    NEED_DESCRIPTION(1, "待描述"),
    NEED_SLOT(2, "待补槽"),
    URGENT(3, "急症分流"),
    CARD_READY(4, "卡片待确认"),
    CONFIRMED(5, "已确认"),
    NEED_HUMAN(6, "需要人工协助");

    /** 对外传递的数字。 */
    private final int code;
    /** 中文说明，给页面展示，不进 JSON。 */
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

    /** JSON 反序列化时按数字找到枚举。 */
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
