package com.moonligh.consult.framework;

/**
 * 框架层状态码，数字看起来像 HTTP，但放在 {@link Result#getCode()} 里。
 * 参数错误、路径不存在、系统异常用这里。
 * 咨询、卡片这类业务失败请用 {@link ErrorCode}。
 */
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未授权，请先登录"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不允许"),
    CONFLICT(409, "请求冲突"),
    FAIL(500, "操作失败"),
    SERVICE_UNAVAILABLE(503, "服务暂时不可用");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    /** 按数字反查。对不上时当作系统失败，避免接口抛空指针。 */
    public static ResultCode fromCode(int code) {
        for (ResultCode item : ResultCode.values()) {
            if (item.code == code) {
                return item;
            }
        }
        return FAIL;
    }
}
