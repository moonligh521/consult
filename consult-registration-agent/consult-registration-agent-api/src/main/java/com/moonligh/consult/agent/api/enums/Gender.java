package com.moonligh.consult.agent.api.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 性别。JSON 与数据库使用 code：1 男，2 女。
 */
public enum Gender {

    MALE(1, "男"),
    FEMALE(2, "女");

    private final int code;
    private final String label;

    Gender(int code, String label) {
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
    public static Gender fromCode(int code) {
        for (Gender item : Gender.values()) {
            if (item.code == code) {
                return item;
            }
        }
        throw new IllegalArgumentException("未知的性别 code: " + code);
    }
}
