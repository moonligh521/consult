package com.moonligh.consult.agent.domain;

import com.moonligh.consult.agent.api.res.ConsultationRes;
import com.moonligh.consult.framework.BusinessException;

/**
 * 锁内办事的结果。
 * 业务可能已经把原话写入会话，同时又要返回 1104、1105。
 * 所以先把响应和错误都收在这里，保存完成后再决定返回还是抛错。
 */
public class SessionWorkResult {

    private final ConsultationRes response;
    private final BusinessException error;

    public SessionWorkResult(ConsultationRes response, BusinessException error) {
        this.response = response;
        this.error = error;
    }

    /** 业务正常结束，带着给前端的画面。 */
    public static SessionWorkResult ofResponse(ConsultationRes response) {
        return new SessionWorkResult(response, null);
    }

    /** 业务要报错，但原话可能已经写进会话，先存着等保存后再抛。 */
    public static SessionWorkResult ofError(BusinessException error) {
        return new SessionWorkResult(null, error);
    }

    public ConsultationRes response() {
        return response;
    }

    public BusinessException error() {
        return error;
    }

    /** 保存完之后，如果这里是 true，仓储再把错误抛给调用方。 */
    public boolean hasError() {
        return error != null;
    }
}
