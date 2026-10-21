package com.moonligh.consult.agent;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

@Component
public class InMemoryConsultationStore {

    private final ConcurrentMap<String, ConsultationSession> sessions = new ConcurrentHashMap<>();

    public void save(ConsultationSession session) {
        sessions.put(session.getId(), session);
    }

    public ConsultationSession find(String id) {
        if (id == null) {
            return null;
        }
        return sessions.get(id);
    }
}
