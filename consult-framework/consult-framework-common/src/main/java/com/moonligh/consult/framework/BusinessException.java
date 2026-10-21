package com.moonligh.consult.framework;

/**
 * 业务失败时抛这个异常，不要在 Controller 里自己拼 {@link Result}。
 * {@link GlobalExceptionHandler} 会拦下来，写成 HTTP 200 的统一返回。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /** 用错误码自带的文案。 */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 错误码不变，文案换成更具体的说明。 */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public int getCode() {
        return errorCode.getCode();
    }
}
