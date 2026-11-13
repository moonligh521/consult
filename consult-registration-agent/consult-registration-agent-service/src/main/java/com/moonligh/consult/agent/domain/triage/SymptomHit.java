package com.moonligh.consult.agent.domain.triage;

import java.util.List;

import com.moonligh.consult.agent.domain.department.SpecialtyRef;

/**
 * 原话里命中的一个目录症状。refs 是它能挂的二级，按目录顺序；歧义词或急症补充词可能为空。
 */
public record SymptomHit(String phrase, boolean ambiguous, List<SpecialtyRef> refs, Span evidence) {

    public SymptomHit {
        refs = refs == null ? List.of() : List.copyOf(refs);
    }
}
