package com.moonligh.consult.framework.observability;

/**
 * 未开启、Collector 不可用或没有 OTel 时的空实现。方法都是空操作。
 */
final class NoopAgentTrace implements AgentTrace {

    private static final AgentGeneration SPAN = new NoopGeneration();

    @Override
    public AgentSpan span(String name) {
        return SPAN;
    }

    @Override
    public AgentGeneration generation(String name) {
        return SPAN;
    }

    @Override
    public void bind(AgentTraceContext context) {
    }

    @Override
    public void clear() {
    }

    @Override
    public String currentTraceId() {
        return null;
    }

    private static final class NoopGeneration implements AgentGeneration {
        @Override
        public AgentGeneration attr(String key, Object value) {
            return this;
        }

        @Override
        public AgentSpan event(String name) {
            return this;
        }

        @Override
        public void fail(String message) {
        }

        @Override
        public void close() {
        }

        @Override
        public AgentGeneration model(String name) {
            return this;
        }

        @Override
        public AgentGeneration input(String prompt) {
            return this;
        }

        @Override
        public AgentGeneration output(String completion) {
            return this;
        }

        @Override
        public AgentGeneration usage(int promptTokens, int completionTokens) {
            return this;
        }
    }
}
