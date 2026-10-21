package com.moonligh.consult.framework;

import java.io.Serial;
import java.io.Serializable;

import org.slf4j.MDC;

/**
 * 接口统一返回。
 * <p>
 * HTTP 状态固定 200，是否成功看 {@link #code}：
 * 成功 code=200，业务数据在 {@link #data}；
 * 失败 code 为错误码，{@link #data} 为 null。
 * {@link #traceId} 从 MDC 取出，方便按一次请求对日志。
 */
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务码，不是 HTTP 状态。200 成功，其它失败。 */
    private int code;
    /** 给前端或调用方看的说明。 */
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
        this.traceId = MDC.get(TraceIdFilter.MDC_KEY);
    }

    /** 成功且没有业务数据，例如确认成功。 */
    public static <T> Result<T> ok() {
        return ok(null);
    }

    /** 成功，并把业务对象放进 data。 */
    public static <T> Result<T> ok(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    /** 失败，用框架默认 500 码，自己写原因。 */
    public static <T> Result<T> fail(String message) {
        return new Result<>(ResultCode.FAIL.getCode(), message, null);
    }

    /** 失败，自己指定数字码和原因。 */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    /** 失败，用框架层状态（参数错误、方法不允许等）。 */
    public static <T> Result<T> fail(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    /** 失败，用业务错误码（找不到咨询、急症停止等）。 */
    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

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
