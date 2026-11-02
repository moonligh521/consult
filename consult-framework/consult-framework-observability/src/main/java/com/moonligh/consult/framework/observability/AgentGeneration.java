package com.moonligh.consult.framework.observability;

/**
 * 一次模型调用。对应 Langfuse generation，不向模型供应商回传内部 trace 头。
 */
public interface AgentGeneration extends AgentSpan {

    @Override
    AgentGeneration attr(String key, Object value);

    AgentGeneration model(String name);

    AgentGeneration input(String prompt);

    AgentGeneration output(String completion);

    AgentGeneration usage(int promptTokens, int completionTokens);
}
