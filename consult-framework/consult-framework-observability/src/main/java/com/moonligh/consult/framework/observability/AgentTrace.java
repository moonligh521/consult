package com.moonligh.consult.framework.observability;

/**
 * Agent 链路的业务入口。实现必须永不抛错、永不阻塞挂号。
 */
public interface AgentTrace {

    AgentTrace NOOP = new NoopAgentTrace();

    AgentSpan span(String name);

    AgentGeneration generation(String name);

    void bind(AgentTraceContext context);

    void clear();

    String currentTraceId();
}
