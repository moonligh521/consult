package com.moonligh.consult.agent.domain;

import com.moonligh.consult.agent.api.res.ConsultationRes;

/**
 * 持有会话锁期间要做的事。必须返回 ConsultationRes。
 */
public interface SessionAction {

    /** 已经拿到会话锁，在这里改会话、做评估。 */
    ConsultationRes run(ConsultationSession session);
}
