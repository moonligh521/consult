package com.moonligh.consult.agent.api.res;

/**
 * 分诊漏斗一行：某节点某来源某原因的次数，以及调了模型的次数和均耗时。
 */
public record TriageFunnelRes(
        int node,
        int source,
        String reason,
        long total,
        long modelCalls,
        long avgModelLatencyMs) {
}
