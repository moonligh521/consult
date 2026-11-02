package com.moonligh.consult.agent.domain.safety;

import java.util.List;

/**
 * 模型补漏端口。只抽信号，不能设置最终安全状态。
 */
public interface SignalExtractor {

    /** 从原话里抽急症信号。不能在这里改会话状态。 */
    SignalExtraction extract(List<ConsultationMessage> transcript, Demographics demographics);
}
