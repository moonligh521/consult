package com.moonligh.consult.agent.domain.safety;

/**
 * 命中证据在某条原话中的位置。不存原文。
 */
public record EvidenceRef(
        /** 哪一条原话。 */
        int messageSeq,
        /** 原文起始下标，含。 */
        int start,
        /** 原文结束下标，不含。 */
        int end) {
}
