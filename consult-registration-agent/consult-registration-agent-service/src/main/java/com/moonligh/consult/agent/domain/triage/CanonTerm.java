package com.moonligh.consult.agent.domain.triage;

/**
 * 归约出的标准病名。ruleId：LITERAL 字面病名，SYN-xxx 同义规则，MODEL 模型。
 */
public record CanonTerm(String term, String ruleId, Span evidence) {

    public static final String LITERAL = "LITERAL";
    public static final String MODEL = "MODEL";
}
