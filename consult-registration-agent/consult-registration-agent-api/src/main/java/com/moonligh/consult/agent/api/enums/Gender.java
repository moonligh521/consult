package com.moonligh.consult.agent.api.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 性别。接口与数据库都传 code，不传枚举名。
 * 1 男；2 女。
 */
public enum Gender {

    MALE(1, "男"),
    FEMALE(2, "女");

    /** 对外传递的数字。 */
    private final int code;
    /** 中文说明，给页面展示，不进 JSON。 */
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

    /** JSON 反序列化时按数字找到枚举。 */
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
