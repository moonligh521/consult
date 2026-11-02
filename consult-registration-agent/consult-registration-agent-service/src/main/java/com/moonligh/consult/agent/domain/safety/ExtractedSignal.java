package com.moonligh.consult.agent.domain.safety;

/**
 * 模型抽出的一个信号。evidence 必须是对应原话的子串。
 */
public record ExtractedSignal(
        /** 目录里的信号名，例如 chest_pain。 */
        String type,
        /** 对应哪一条原话。 */
        int messageSeq,
        /** 原话里的证据片段。 */
        String evidence,
        /** 时间：current / past / resolved / unknown。 */
        String temporalStatus,
        /** 极性：positive / negated / unclear。 */
        String polarity,
        /** 主语，通常是 patient。 */
        String subject,
        /** 不确定度：low / high。 */
        String uncertainty) {
}
