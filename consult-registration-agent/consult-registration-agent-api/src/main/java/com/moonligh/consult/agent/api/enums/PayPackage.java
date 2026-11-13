package com.moonligh.consult.agent.api.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 卡片确认后的咨询档位。JSON 与数据库使用 code：
 * 1 普通咨询，2 极速咨询，3 三甲医生，4 权威专家。
 */
public enum PayPackage {

    NORMAL(1, "普通咨询"),
    FAST(2, "极速咨询"),
    TERTIARY(3, "三甲医生"),
    EXPERT(4, "权威专家");

    /** 对外传递的数字。 */
    private final int code;
    /** 中文名称，给页面展示，不进 JSON。 */
    private final String label;

    PayPackage(int code, String label) {
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

    /** JSON 里的 1、2、3、4 转成枚举。 */
    @JsonCreator
    public static PayPackage fromCode(int code) {
        for (PayPackage item : values()) {
            if (item.code == code) {
                return item;
            }
        }
        throw new IllegalArgumentException("未知的咨询档位 code: " + code);
    }
}
