package com.moonligh.consult.agent.infrastructure.model;

/**
 * 模型调用的用途。各自限流、熔断、超时、提示词。
 * <p>
 * 三层裁决：程序能明确就不调模型；程序不定才按用途调；模型失败走该节点写死的兜底。
 * 安全与主诉绝不混在一次调用里——失败策略不同（安全兜底停导诊，挂科兜底全科）。
 */
public enum ModelPurpose {

    /** 急症/风险信号补漏。仅规则未钉死且命中触发词时调用；不能否掉已明确急症。 */
    SAFETY,
    /** 主诉归约 + 挂科建议（一次调用两字段）。仅 N4/N6 规则 UNCERTAIN 时调用。 */
    COMPLAINT
}
