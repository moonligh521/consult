package com.moonligh.consult.agent.domain.safety;

/**
 * 安全决策审计端口。实现不得把原话写入日志。
 */
public interface SafetyAuditRecorder {

    void record(SafetyDecisionAudit audit);
}
