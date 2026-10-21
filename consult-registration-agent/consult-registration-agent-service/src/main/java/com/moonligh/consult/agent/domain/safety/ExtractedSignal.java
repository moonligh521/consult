package com.moonligh.consult.agent.domain.safety;

/**
 * 模型抽出的一个信号。evidence 必须是对应原话的子串。
 */
public record ExtractedSignal(
        String type,
        int messageSeq,
        String evidence,
        String temporalStatus,
        String polarity,
        String subject,
        String uncertainty) {
}
