package com.moonligh.consult.agent.domain.triage;

/**
 * 证据位置。下标相对匹配串 {@code SpokenNormalizer.forMatch(description)}，不落原文。
 */
public record Span(int start, int end) {
}
