package com.moonligh.consult.agent.domain.triage;

import java.util.List;

/**
 * 一次分诊的产出：要持久化的结论 + 要落库的逐节点决策。
 */
public record TriageRun(TriageResult result, List<MatchDecision> decisions) {

    public TriageRun {
        decisions = decisions == null ? List.of() : List.copyOf(decisions);
    }
}
