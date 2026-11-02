package com.moonligh.consult.framework.observability;

/**
 * 一次咨询评估的关联键。只进 span 属性，不进 Prometheus 标签。
 */
public record AgentTraceContext(
        String consultationId,
        String runId,
        Integer messageSeq,
        String decisionId,
        String catalogVersion,
        String promptVersion,
        String userId) {

    public AgentTraceContext(
            String consultationId,
            String runId,
            Integer messageSeq,
            String decisionId,
            String catalogVersion,
            String promptVersion) {
        this(consultationId, runId, messageSeq, decisionId, catalogVersion, promptVersion, null);
    }

    public static AgentTraceContext consultation(String consultationId) {
        return new AgentTraceContext(consultationId, null, null, null, null, null, null);
    }
}
