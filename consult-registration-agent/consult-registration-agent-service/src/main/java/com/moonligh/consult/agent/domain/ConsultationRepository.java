package com.moonligh.consult.agent.domain;

import com.moonligh.consult.agent.api.res.ConsultationRes;

/**
 * 咨询会话仓储。同一咨询的请求串行执行。
 */
public interface ConsultationRepository {

    /**
     * 加锁加载或创建会话，执行 {@code action} 后保存。
     * {@code consultationId} 为空时新建；不存在时抛 1101。
     * action 中的业务异常在保存成功后再抛出。
     */
    ConsultationRes runLocked(String consultationId, SessionAction action);
}
