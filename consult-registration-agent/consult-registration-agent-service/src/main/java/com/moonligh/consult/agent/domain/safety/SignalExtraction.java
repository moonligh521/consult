package com.moonligh.consult.agent.domain.safety;

import java.util.List;

import com.moonligh.consult.framework.model.ModelStatus;

/**
 * 模型补漏的结果。失败时 status 不是 SUCCESS，signals 为空。
 */
public record SignalExtraction(
        /** 这次调用成功还是失败。 */
        ModelStatus status,
        /** 用的模型名。没调用为 null。 */
        String modelVersion,
        /** 抽出的信号。失败时为空。 */
        List<ExtractedSignal> signals,
        /** 模型自己说还有说不清的地方。 */
        boolean unresolvedAmbiguity) {

    /** 模型没开。 */
    public static SignalExtraction disabled() {
        return new SignalExtraction(ModelStatus.DISABLED, null, List.of(), false);
    }

    /** 本轮不需要调模型。 */
    public static SignalExtraction skipped() {
        return new SignalExtraction(ModelStatus.SKIPPED, null, List.of(), false);
    }

    /** 调不通。 */
    public static SignalExtraction unavailable() {
        return new SignalExtraction(ModelStatus.UNAVAILABLE, null, List.of(), false);
    }

    /** 超时。 */
    public static SignalExtraction timeout() {
        return new SignalExtraction(ModelStatus.TIMEOUT, null, List.of(), false);
    }

    /** 输出不合格。 */
    public static SignalExtraction invalid() {
        return new SignalExtraction(ModelStatus.INVALID_OUTPUT, null, List.of(), false);
    }
}
