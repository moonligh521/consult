package com.moonligh.consult.agent.api.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 下一个待补槽位。接口与数据库都传 code，不传枚举名。
 * 1 性别；2 年龄；3 病程。
 */
public enum MissingSlot {

    GENDER(1, "性别"),
    AGE(2, "年龄"),
    DURATION(3, "病程");

    private final int code;
    private final String label;

    MissingSlot(int code, String label) {
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
    public static MissingSlot fromCode(int code) {
        for (MissingSlot item : MissingSlot.values()) {
            if (item.code == code) {
                return item;
            }
        }
        throw new IllegalArgumentException("未知的槽位 code: " + code);
    }
}
