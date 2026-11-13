package com.moonligh.consult.agent.domain.triage;

import java.util.List;

/**
 * N6 命中诊断。直接记录挂词和位置，不要从最终科室反推。
 */
public record RouteHit(
        String keyword,
        int position,
        String specialty,
        List<String> compared,
        String winReason) {

    public RouteHit {
        compared = compared == null ? List.of() : List.copyOf(compared);
        position = keyword == null ? -1 : position;
    }
}
