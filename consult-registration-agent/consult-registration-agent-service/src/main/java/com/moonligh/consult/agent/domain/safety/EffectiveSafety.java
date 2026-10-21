package com.moonligh.consult.agent.domain.safety;

/**
 * 当前有效的紧急程度和渠道。
 */
public record EffectiveSafety(Urgency urgency, ChannelDecision channel) {
}
