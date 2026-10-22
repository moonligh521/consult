package com.moonligh.consult.agent.domain.safety;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 进程内审计记录，供单元测试断言使用。
 */
public class InMemorySafetyAuditRecorder {

    private final ConcurrentMap<String, List<SafetyDecisionAudit>> audits = new ConcurrentHashMap<>();

    public void record(SafetyDecisionAudit audit) {
        audits.computeIfAbsent(audit.consultationId(), key -> new ArrayList<>()).add(audit);
    }

    public List<SafetyDecisionAudit> list(String consultationId) {
        return List.copyOf(audits.getOrDefault(consultationId, List.of()));
    }
}
