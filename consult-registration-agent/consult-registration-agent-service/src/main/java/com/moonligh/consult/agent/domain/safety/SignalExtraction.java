package com.moonligh.consult.agent.domain.safety;

import java.util.List;

/**
 * 模型补漏的结果。失败时 status 不是 SUCCESS，signals 为空。
 */
public record SignalExtraction(
        ModelStatus status,
        String modelVersion,
        List<ExtractedSignal> signals,
        boolean unresolvedAmbiguity) {

    public static SignalExtraction disabled() {
        return new SignalExtraction(ModelStatus.DISABLED, null, List.of(), false);
    }

    public static SignalExtraction skipped() {
        return new SignalExtraction(ModelStatus.SKIPPED, null, List.of(), false);
    }

    public static SignalExtraction unavailable() {
        return new SignalExtraction(ModelStatus.UNAVAILABLE, null, List.of(), false);
    }

    public static SignalExtraction timeout() {
        return new SignalExtraction(ModelStatus.TIMEOUT, null, List.of(), false);
    }

    public static SignalExtraction invalid() {
        return new SignalExtraction(ModelStatus.INVALID_OUTPUT, null, List.of(), false);
    }
}
