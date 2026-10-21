package com.moonligh.consult.agent.api.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 卡片交给下游时的任务性质。不是互联网诊疗接诊。
 * 1 预约前信息整理；2 人工/医师预审。
 */
public enum Handoff {

    PRE_APPOINTMENT_INFO(1, "预约前信息整理"),
    PRECHECK(2, "人工/医师预审");

    private final int code;
    private final String label;

    Handoff(int code, String label) {
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
    public static Handoff fromCode(int code) {
        for (Handoff item : values()) {
            if (item.code == code) {
                return item;
            }
        }
        throw new IllegalArgumentException("未知的交接类型 code: " + code);
    }
}
