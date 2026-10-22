package com.moonligh.consult.agent.domain;

import com.moonligh.consult.agent.api.res.ConsultationRes;

/**
 * 持有会话锁期间要做的事。
 * 必须返回 ConsultationRes，不要再用泛型 T 把返回类型藏起来。
 */
public interface SessionAction {

    ConsultationRes run(ConsultationSession session);
}
