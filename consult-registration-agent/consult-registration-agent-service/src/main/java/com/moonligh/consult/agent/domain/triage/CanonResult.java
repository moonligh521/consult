package com.moonligh.consult.agent.domain.triage;

import java.util.ArrayList;
import java.util.List;

/**
 * N4 主诉归约的结论。
 * resolved=false 表示规则定不了、模型也没给出结果：下游挂科不能信孤立症状，只能兜底全科。
 */
public record CanonResult(
        List<CanonTerm> terms,
        List<CanonTerm> historyTerms,
        List<SymptomHit> symptoms,
        List<String> shadowRuleIds,
        Certainty certainty,
        DecisionSource source,
        String reason,
        boolean resolved) {

    public CanonResult {
        terms = terms == null ? List.of() : List.copyOf(terms);
        historyTerms = historyTerms == null ? List.of() : List.copyOf(historyTerms);
        symptoms = symptoms == null ? List.of() : List.copyOf(symptoms);
        shadowRuleIds = shadowRuleIds == null ? List.of() : List.copyOf(shadowRuleIds);
    }

    public boolean certain() {
        return certainty == Certainty.CERTAIN;
    }

    public List<String> termNames() {
        List<String> names = new ArrayList<>();
        for (CanonTerm term : terms) {
            names.add(term.term());
        }
        return names;
    }

    /** 换掉病名和来源，症状与影子保留。 */
    public CanonResult with(List<CanonTerm> newTerms, DecisionSource newSource, String newReason, boolean newResolved) {
        return new CanonResult(newTerms, historyTerms, symptoms, shadowRuleIds, certainty, newSource, newReason, newResolved);
    }
}
