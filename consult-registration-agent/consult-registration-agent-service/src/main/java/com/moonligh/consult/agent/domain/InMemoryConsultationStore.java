package com.moonligh.consult.agent.domain;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

/**
 * 把咨询会话放在进程内存里。重启就没了，V0.1 还不写数据库。
 * ConcurrentHashMap 是为了同时处理多个请求时不互相覆盖。
 */
@Component
public class InMemoryConsultationStore {

    private final ConcurrentMap<String, ConsultationSession> sessions = new ConcurrentHashMap<>();

    public void save(ConsultationSession session) {
        sessions.put(session.getId(), session);
    }

    /**
     * 找不到返回 null，由调用方决定要不要报 1101。
     */
    public ConsultationSession find(String id) {
        if (id == null) {
            return null;
        }
        return sessions.get(id);
    }
}
