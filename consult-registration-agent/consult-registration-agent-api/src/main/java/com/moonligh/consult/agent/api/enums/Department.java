package com.moonligh.consult.agent.api.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 推荐科室。JSON 与数据库使用 code：1 呼吸内科，2 神经内科，3 消化内科，4 全科医学科。
 */
public enum Department {

    RESPIRATORY(1, "呼吸内科"),
    NEUROLOGY(2, "神经内科"),
    DIGESTIVE(3, "消化内科"),
    GENERAL(4, "全科医学科");

    private final int code;
    private final String label;

    Department(int code, String label) {
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
    public static Department fromCode(int code) {
        for (Department item : Department.values()) {
            if (item.code == code) {
                return item;
            }
        }
        throw new IllegalArgumentException("未知的科室 code: " + code);
    }
}
