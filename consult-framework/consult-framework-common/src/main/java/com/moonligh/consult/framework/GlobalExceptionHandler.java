package com.moonligh.consult.framework;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * 全局异常处理：Controller 抛出的异常都在这里变成 Result。
 * HTTP 状态保持 200，前端只看 body 里的 code。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务规则不满足，例如找不到咨询、急症停止。 */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException ex) {
        log.warn("业务异常: code={} msg={}", ex.getCode(), ex.getMessage());
        return Result.fail(ex.getCode(), ex.getMessage());
    }

    /** 参数不合法，例如枚举数字对不上。 */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("参数异常: {}", ex.getMessage());
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), ex.getMessage());
    }

    /** @Valid 校验失败。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException ex) {
        String message = joinFieldErrors(ex.getBindingResult().getFieldErrors());
        log.warn("参数校验失败: {}", message);
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), message);
    }

    /** 表单绑定失败。 */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBind(BindException ex) {
        String message = joinFieldErrors(ex.getFieldErrors());
        log.warn("参数绑定失败: {}", message);
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), message);
    }

    /** 请求少了必填参数。 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException ex) {
        log.warn("缺少必填参数: {}", ex.getParameterName());
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), "缺少必填参数: " + ex.getParameterName());
    }

    /** JSON 解析失败。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleBadBody(HttpMessageNotReadableException ex) {
        log.warn("请求体解析失败: {}", ex.getMessage());
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), "请求体格式错误");
    }

    /** 用错了 HTTP 方法，例如该 POST 却发了 GET。 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethod(HttpRequestMethodNotSupportedException ex) {
        log.warn("方法不允许: {}", ex.getMethod());
        return Result.fail(ResultCode.METHOD_NOT_ALLOWED);
    }

    /** 路径不存在。 */
    @ExceptionHandler(NoHandlerFoundException.class)
    public Result<Void> handleNotFound(NoHandlerFoundException ex) {
        log.warn("接口不存在: {} {}", ex.getHttpMethod(), ex.getRequestURL());
        return Result.fail(ResultCode.NOT_FOUND.getCode(), "接口不存在");
    }

    /** 最后兜底。打 error 日志，不把堆栈细节返回给前端。 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleUnknown(Exception ex) {
        log.error("系统异常", ex);
        return Result.fail(ResultCode.FAIL.getCode(), "操作失败，请稍后重试");
    }

    /** 把多个字段错误拼成一句，方便前端展示。 */
    private String joinFieldErrors(java.util.List<FieldError> errors) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < errors.size(); i++) {
            if (i > 0) {
                builder.append("; ");
            }
            builder.append(errors.get(i).getDefaultMessage());
        }
        return builder.toString();
    }
}
