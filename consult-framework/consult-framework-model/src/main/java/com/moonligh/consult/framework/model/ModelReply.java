package com.moonligh.consult.framework.model;

/**
 * 一次模型调用的原始结果。content 是模型回复正文，失败时为空。
 */
public record ModelReply(
        ModelStatus status,
        String content,
        long latencyMs,
        String modelName,
        boolean cached,
        int promptTokens,
        int completionTokens) {

    public ModelReply(ModelStatus status, String content, long latencyMs, String modelName, boolean cached) {
        this(status, content, latencyMs, modelName, cached, 0, 0);
    }

    public static ModelReply failed(ModelStatus status, long latencyMs, String modelName) {
        return new ModelReply(status, null, latencyMs, modelName, false, 0, 0);
    }
}
