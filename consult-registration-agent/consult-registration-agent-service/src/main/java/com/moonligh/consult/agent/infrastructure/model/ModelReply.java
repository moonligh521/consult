package com.moonligh.consult.agent.infrastructure.model;

import com.moonligh.consult.agent.domain.safety.ModelStatus;

/**
 * 一次模型调用的原始结果。content 是模型回复正文，失败时为空。
 */
public record ModelReply(ModelStatus status, String content, long latencyMs, String modelName, boolean cached) {

    public static ModelReply failed(ModelStatus status, long latencyMs, String modelName) {
        return new ModelReply(status, null, latencyMs, modelName, false);
    }
}
