package com.moonligh.consult.framework.observability;

/**
 * 一次业务步骤。关闭时结束 span，允许嵌套。
 */
public interface AgentSpan extends AutoCloseable {

    AgentSpan attr(String key, Object value);

    AgentSpan event(String name);

    void fail(String message);

    @Override
    void close();
}
