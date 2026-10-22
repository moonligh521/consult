package com.moonligh.consult.agent.domain;

import com.moonligh.consult.agent.api.res.ConsultationRes;

/**
 * 持有会话锁期间执行的业务。
 */
public interface SessionAction {

    ConsultationRes run(ConsultationSession session);
}
