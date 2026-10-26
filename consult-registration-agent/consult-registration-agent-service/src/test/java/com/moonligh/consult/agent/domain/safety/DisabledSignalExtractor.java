package com.moonligh.consult.agent.domain.safety;

import java.util.List;

/**
 * 模型关闭时的空实现。不算故障。
 */
public class DisabledSignalExtractor implements SignalExtractor {

    /** 直接返回 DISABLED，不调任何模型。 */
    @Override
    public SignalExtraction extract(List<ConsultationMessage> transcript, Demographics demographics) {
        return SignalExtraction.disabled();
    }
}
