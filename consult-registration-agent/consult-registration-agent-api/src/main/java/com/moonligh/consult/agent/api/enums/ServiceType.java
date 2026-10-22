package com.moonligh.consult.agent.api.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 用户要办理的服务。由前端入口传入，不由模型从病情推断。
 * 1 线下门诊挂号；2 互联网复诊；3 远程会诊。
 */
public enum ServiceType {

    OFFLINE_APPOINTMENT(1, "线下门诊挂号"),
    ONLINE_FOLLOW_UP(2, "互联网复诊"),
    REMOTE_CONSULT(3, "远程会诊");

    /** 对外传递的数字。1 线下挂号，2 互联网复诊，3 远程会诊。 */
    private final int code;
    /** 中文名称，给页面展示，不进 JSON。 */
    private final String label;

    ServiceType(int code, String label) {
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

    /** JSON 里的 1、2、3 转成枚举。对不上就是 1114。 */
    @JsonCreator
    public static ServiceType fromCode(int code) {
        for (ServiceType item : values()) {
            if (item.code == code) {
                return item;
            }
        }
        throw new IllegalArgumentException("未知的服务类型 code: " + code);
    }
}
