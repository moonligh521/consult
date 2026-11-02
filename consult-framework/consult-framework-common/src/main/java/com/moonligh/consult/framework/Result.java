package com.moonligh.consult.framework;

import java.io.Serial;
import java.io.Serializable;

import org.slf4j.MDC;

/**
 * 接口统一返回。
 * HTTP 状态固定 200，是否成功看 code：200 成功，其它是错误码。
 */
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务码，不是 HTTP 状态。200 成功，其它失败。 */
    private int code;
    /** 给调用方看的说明。 */
    private String message;
    /** 成功时的业务数据；失败时为 null。 */
    private T data;
    /** 本次请求的追踪号，和日志、响应头 X-Trace-Id 是同一个值。 */
    private String traceId;

    public Result() {
    }

    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        // 过滤器已经把追踪号放进 MDC，这里原样带给前端。
        this.traceId = MDC.get(TraceIdFilter.MDC_KEY);
    }

    /** 成功，没有业务数据。 */
    public static <T> Result<T> ok() {
        return ok(null);
    }

    /** 成功，data 是业务对象。 */
    public static <T> Result<T> ok(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    /** 失败，用 500 和自定义说明。 */
    public static <T> Result<T> fail(String message) {
        return new Result<>(ResultCode.FAIL.getCode(), message, null);
    }

    /** 失败，指定错误码和说明。 */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    /** 失败，用框架状态码。 */
    public static <T> Result<T> fail(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    /** 失败，用业务错误码。 */
    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    /** code 是 200 才算成功。 */
    public boolean isSuccess() {
        return this.code == ResultCode.SUCCESS.getCode();
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}
