package com.moonligh.consult.agent.domain.safety;

/**
 * 内部渠道适用性。不直接出现在接口里。
 */
public enum ChannelDecision {

    ALLOWED,
    PRECHECK_REQUIRED,
    NEED_HUMAN,
    UNSUITABLE_FOR_ONLINE
}
