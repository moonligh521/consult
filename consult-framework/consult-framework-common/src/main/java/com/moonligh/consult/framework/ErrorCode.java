package com.moonligh.consult.framework;

/**
 * 业务错误码，失败时放在 Result 的 code 里。
 * 1xxx 通用，11xx 挂号咨询。接口和数据库都传数字。
 */
public enum ErrorCode {

    UNKNOWN_ERROR(1000, "未知错误"),
    PARAM_INVALID(1001, "参数校验失败"),
    PARAM_MISSING(1002, "缺少必填参数"),
    TOO_MANY_REQUESTS(1003, "请求太频繁，请稍后再试。"),
    SERVICE_BUSY(1004, "服务繁忙，请稍后再试。"),

    CONSULTATION_NOT_FOUND(1101, "找不到这轮咨询。"),
    CARD_NOT_READY(1102, "咨询卡片尚未生成。"),
    URGENT_STOP(1103, "请先寻求紧急医疗帮助。"),
    CARD_PENDING_CONFIRMATION(1104, "请在卡片上修改或选择咨询档位。"),
    ALREADY_CONFIRMED(1105, "这轮咨询已经确认。"),
    INVALID_GENDER(1106, "性别须为 1 或 2。"),
    INVALID_AGE(1107, "年龄须为 1 到 120 的整数。"),
    INVALID_DURATION(1108, "病程须包含时长。"),
    EMPTY_CARD_PATCH(1109, "请至少修改一项。"),
    HUMAN_STOP(1110, "请转人工协助。"),
    SAFE_FALLBACK_STOP(1111, "系统暂时无法完成安全分流，请转人工协助。"),
    UNSUITABLE_STOP(1112, "当前线上渠道不适用，请选择线下就诊或转人工。"),
    ORDER_NOT_ALLOWED(1115, "请先确认咨询卡片。"),
    INVALID_PACKAGE(1116, "咨询档位须为 1、2、3 或 4。"),
    ALREADY_ORDERED(1117, "这轮咨询已经下过单。"),
    INQUIRY_CLOSED(1118, "当前问诊通道正在维护，请稍后再试。"),
    INVALID_DEPARTMENT(1119, "该科室当前未开通。"),
    PATIENT_NOT_FOUND(1120, "找不到这位就诊人。"),
    INVALID_PATIENT(1121, "请填写就诊人姓名、性别和年龄。"),
    INVALID_CONSULT_MINUTES(1122, "问诊时长须为 5、10、15、20 或 30 分钟。"),
    CONSULT_ENDED(1123, "本次问诊已结束。"),
    CONSULT_NOT_OWNED(1124, "找不到这轮咨询。"),
    CANNOT_DISABLE_FALLBACK(1125, "兜底全科不能停用。"),
    SPECIALTY_NO_COVERAGE(1126, "该科室医生或当日号源不足，暂不能开通。"),
    CONSULT_IN_PROGRESS(1127, "上一步还在处理，请稍候再试。"),

    LOGIN_REQUIRED(1201, "请先登录。"),
    INVALID_MOBILE(1202, "请输入正确的手机号。"),
    INVALID_CAPTCHA(1203, "验证码不正确或已过期。"),
    SMS_TOO_FREQUENT(1204, "验证码发送过于频繁，请稍后再试。"),
    ACCOUNT_DISABLED(1205, "账号已停用。"),
    PERSON_ALREADY_BOUND(1206, "该就诊人已在当前账号下。"),
    STAFF_LOGIN_FAILED(1207, "用户名或密码不正确。"),

    DOCTOR_NOT_FOUND(1301, "找不到这位医生。"),
    INVALID_DOCTOR(1302, "请填写医生姓名、职称和科室。"),
    SCHEDULE_NOT_FOUND(1303, "找不到这条排班。"),
    SCHEDULE_CONFLICT(1304, "该医生此时段已有排班。"),
    DOCTOR_UNAVAILABLE(1305, "当前暂无合适医生，请稍后再试。");

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
