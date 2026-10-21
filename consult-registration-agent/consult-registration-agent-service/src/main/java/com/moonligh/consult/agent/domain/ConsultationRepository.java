package com.moonligh.consult.agent.domain;

import java.util.function.Function;

/**
 * 咨询聚合仓储。同一会话的请求在锁内串行执行。
 */
public interface ConsultationRepository {

    /**
     * consultationId 为空则新建；找不到已有编号则抛 1101。
     * work 里抛出的 BusinessException 必须在持久化提交之后再抛给调用方。
     */
    <T> T inLock(String consultationId, Function<ConsultationSession, T> work);
}
