package com.moonligh.consult.agent.api.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 性别。JSON 与数据库使用 code：1 男，2 女。
 */
public enum Gender {

    MALE(1, "男"),
    FEMALE(2, "女");

    /** 对外传递的数字。1 男，2 女。 */
    private final int code;
    /** 中文名称，给页面展示，不进 JSON。 */
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

    /** JSON 和数据库里的数字转成枚举。1 男，2 女。 */
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
