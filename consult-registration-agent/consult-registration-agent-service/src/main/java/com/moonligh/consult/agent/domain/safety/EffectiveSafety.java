package com.moonligh.consult.agent.domain.safety;

/**
 * 当前有效的紧急程度和渠道。
 */
public record EffectiveSafety(
        /** 急症优先于尽快评估。 */
        Urgency urgency,
        /** 转人工优先于渠道不适用。 */
        ChannelDecision channel) {
}
