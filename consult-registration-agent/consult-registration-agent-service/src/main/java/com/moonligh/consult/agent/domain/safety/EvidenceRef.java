package com.moonligh.consult.agent.domain.safety;

/**
 * 命中证据在某条原话中的位置。不存原文。
 */
public record EvidenceRef(int messageSeq, int start, int end) {
}
