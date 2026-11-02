package com.moonligh.consult.agent.domain.triage;

/**
 * 模型输出被校验丢掉的一条。原因用于决定改提示词还是改候选/校验。
 */
public record ValidationReject(String kind, String value, String reason) {

    public static final String TERM = "term";
    public static final String SPECIALTY = "specialty";
    public static final String NOT_IN_CATALOG = "NOT_IN_CATALOG";
    public static final String EVIDENCE_NOT_SUBSTRING = "EVIDENCE_NOT_SUBSTRING";
    public static final String NOT_IN_CANDIDATES = "NOT_IN_CANDIDATES";
    public static final String INVALID_FORMAT = "INVALID_FORMAT";
}
