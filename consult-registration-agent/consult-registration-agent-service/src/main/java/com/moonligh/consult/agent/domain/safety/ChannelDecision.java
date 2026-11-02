package com.moonligh.consult.agent.domain.safety;

/**
 * 内部渠道适用性。不直接出现在接口里。
 */
public enum ChannelDecision {

    /** 当前渠道可以继续。 */
    ALLOWED,
    /** 需要转人工。 */
    NEED_HUMAN
}
