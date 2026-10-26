package com.moonligh.consult.agent.infrastructure.model;

/**
 * 模型调用的用途。各自限流、熔断、超时。
 */
public enum ModelPurpose {

    /** 急症信号补漏。 */
    SAFETY,
    /** 主诉归约 + 挂科建议。 */
    COMPLAINT
}
