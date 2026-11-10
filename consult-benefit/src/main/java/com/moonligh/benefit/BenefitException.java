package com.moonligh.benefit;

/** 权益服务里的业务错误。code 给调用方，不带内部细节。 */
public class BenefitException extends RuntimeException {

    private final String code;

    public BenefitException(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
