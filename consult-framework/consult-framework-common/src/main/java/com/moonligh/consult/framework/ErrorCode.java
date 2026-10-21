package com.moonligh.consult.framework;

/**
 * 业务错误码，失败时放在 {@link Result#getCode()}。
 * 分段方便以后加码：1xxx 通用，11xx 挂号咨询。
 * 接口 JSON 和数据库都传数字，不传枚举名。
 */
public enum ErrorCode {

    UNKNOWN_ERROR(1000, "未知错误"),
    PARAM_INVALID(1001, "参数校验失败"),
    PARAM_MISSING(1002, "缺少必填参数"),

    CONSULTATION_NOT_FOUND(1101, "找不到这轮咨询。"),
    CARD_NOT_READY(1102, "咨询卡片尚未生成。"),
    URGENT_STOP(1103, "请先寻求紧急医疗帮助。"),
    CARD_PENDING_CONFIRMATION(1104, "请先确认或修改咨询卡片。"),
    ALREADY_CONFIRMED(1105, "这轮咨询已经确认。"),
    INVALID_GENDER(1106, "性别须为 1 或 2。"),
    INVALID_AGE(1107, "年龄须为 1 到 120 的整数。"),
    INVALID_DURATION(1108, "病程须包含时长。"),
    EMPTY_CARD_PATCH(1109, "请至少修改一项。");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    /** 按数字反查。对不上时返回未知错误，不要返回 null。 */
    public static ErrorCode fromCode(int code) {
        for (ErrorCode item : ErrorCode.values()) {
            if (item.code == code) {
                return item;
            }
        }
        return UNKNOWN_ERROR;
    }
}
