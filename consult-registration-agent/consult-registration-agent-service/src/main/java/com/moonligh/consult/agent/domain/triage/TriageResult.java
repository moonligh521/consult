package com.moonligh.consult.agent.domain.triage;

import java.util.List;

import com.moonligh.consult.agent.domain.safety.ModelStatus;

/**
 * 最近一次分诊结论，存在会话 triage_json。派医生只读这里，不再调模型。
 */
public record TriageResult(
        String runId,
        String catalogVersion,
        String promptVersion,
        String modelName,
        String fingerprint,
        CanonResult canon,
        RouteDecision route,
        ModelStatus modelStatus,
        long modelLatencyMs,
        boolean modelCached) {

    /** 派医生用的归约病名。 */
    public List<String> canonicalTerms() {
        return canon == null ? List.of() : canon.termNames();
    }

    /** 患者改了科：病名保留，挂科换成患者选的。 */
    public TriageResult withRoute(RouteDecision newRoute) {
        return new TriageResult(runId, catalogVersion, promptVersion, modelName, fingerprint, canon, newRoute,
                modelStatus, modelLatencyMs, modelCached);
    }
}
