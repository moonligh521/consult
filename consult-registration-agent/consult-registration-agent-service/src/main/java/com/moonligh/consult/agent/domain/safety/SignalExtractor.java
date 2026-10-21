package com.moonligh.consult.agent.domain.safety;

import java.util.List;

/**
 * 模型补漏端口。只抽信号，不能设置最终安全状态。
 */
public interface SignalExtractor {

    SignalExtraction extract(List<ConsultationMessage> transcript, Demographics demographics);
}
