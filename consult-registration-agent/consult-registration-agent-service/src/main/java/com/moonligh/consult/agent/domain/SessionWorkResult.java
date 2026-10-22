package com.moonligh.consult.agent.domain;

import com.moonligh.consult.agent.api.res.ConsultationRes;
import com.moonligh.consult.framework.BusinessException;

/**
 * 锁内执行结果。业务异常与响应一并带回，供提交后再抛出。
 */
public class SessionWorkResult {

    private final ConsultationRes response;
    private final BusinessException error;

    public SessionWorkResult(ConsultationRes response, BusinessException error) {
        this.response = response;
        this.error = error;
    }

    public static SessionWorkResult ofResponse(ConsultationRes response) {
        return new SessionWorkResult(response, null);
    }

    public static SessionWorkResult ofError(BusinessException error) {
        return new SessionWorkResult(null, error);
    }

    public ConsultationRes response() {
        return response;
    }

    public BusinessException error() {
        return error;
    }

    public boolean hasError() {
        return error != null;
    }
}
