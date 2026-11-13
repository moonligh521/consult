package com.moonligh.pay.domain;

/** 支付服务自己的错误。code 原样返回给调用方。 */
public final class PayException extends RuntimeException {

    private final String code;

    public PayException(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
