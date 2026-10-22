package com.moonligh.consult.framework;

/**
 * 业务错误码，失败时放在 Result 的 code 里。
 * 1xxx 通用，11xx 挂号咨询。接口和数据库都传数字。
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
    EMPTY_CARD_PATCH(1109, "请至少修改一项。"),
    HUMAN_STOP(1110, "请转人工协助。"),
    SAFE_FALLBACK_STOP(1111, "系统暂时无法完成安全分流，请转人工协助。"),
    UNSUITABLE_STOP(1112, "当前线上渠道不适用，请选择线下就诊或转人工。"),
    SERVICE_TYPE_LOCKED(1113, "卡片确认后不能修改服务类型。"),
    INVALID_SERVICE_TYPE(1114, "服务类型须为 1、2 或 3。"),
    ORDER_NOT_ALLOWED(1115, "请先确认咨询卡片。"),
    INVALID_PACKAGE(1116, "咨询档位须为 1、2、3 或 4。"),
    ALREADY_ORDERED(1117, "这轮咨询已经下过单。"),
    INQUIRY_CLOSED(1118, "当前问诊通道正在维护，请稍后再试。"),
    INVALID_DEPARTMENT(1119, "该科室当前未开通。");

    /** 放进 Result.code 的数字。 */
    private final int code;
    /** 给调用方看的说明。 */
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
}
