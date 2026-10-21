package com.moonligh.consult.agent.domain.safety;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

/**
 * 进程内审计。测试用来断言字段，不含原话。
 */
@Component
public class InMemorySafetyAuditRecorder implements SafetyAuditRecorder {

    private final ConcurrentMap<String, List<SafetyDecisionAudit>> audits = new ConcurrentHashMap<>();

    @Override
    public void record(SafetyDecisionAudit audit) {
        audits.computeIfAbsent(audit.consultationId(), key -> new ArrayList<>()).add(audit);
    }

    public List<SafetyDecisionAudit> list(String consultationId) {
        return List.copyOf(audits.getOrDefault(consultationId, List.of()));
    }
}
