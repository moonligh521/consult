package com.moonligh.consult.agent.domain.triage;

import java.util.List;

import com.moonligh.consult.agent.domain.safety.ModelStatus;

/**
 * 主诉模型一次回复。terms 与 specialty 是模型原样输出，引擎再校验；校验不过整段丢弃。
 */
public record ComplaintReply(
        ModelStatus status,
        List<ModelTerm> terms,
        String specialty,
        String specialtyEvidence,
        long latencyMs,
        boolean cached,
        String promptVersion,
        String modelName) {

    public ComplaintReply {
        terms = terms == null ? List.of() : List.copyOf(terms);
    }

    /** 规则已定，没调模型。 */
    public static ComplaintReply notCalled() {
        return new ComplaintReply(ModelStatus.SKIPPED, List.of(), null, null, 0, false, null, null);
    }

    public static ComplaintReply failed(ModelStatus status, long latencyMs, String promptVersion, String modelName) {
        return new ComplaintReply(status, List.of(), null, null, latencyMs, false, promptVersion, modelName);
    }

    public boolean called() {
        return status != ModelStatus.SKIPPED;
    }

    /** 模型给的一个病名和它引用的原话。 */
    public record ModelTerm(String term, String evidence) {
    }
}
