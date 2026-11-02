package com.moonligh.consult.agent.api.res;

/**
 * 管理端深链到 Langfuse。available=false 时 reason 说明为什么不能打开。
 */
public record AgentTraceLinkRes(
        boolean available,
        String reason,
        String url,
        String consultationId,
        String runId) {

    public static AgentTraceLinkRes missing(String reason, String consultationId, String runId) {
        return new AgentTraceLinkRes(false, reason, null, consultationId, runId);
    }
}
