package com.moonligh.consult.agent.domain;

import com.moonligh.consult.agent.api.res.ConsultationRes;
import com.moonligh.consult.framework.BusinessException;

/**
 * 锁内办事的结果。
 * <p>
 * 业务可能已经把原话写入会话，同时又要返回 1104、1105 这类错误。
 * 所以先把响应和错误都收在这个对象里，保存完成后再决定返回还是抛错。
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
