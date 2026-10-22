package com.moonligh.consult.agent.domain.safety;

/**
 * 内部渠道适用性。不直接出现在接口里。
 */
public enum ChannelDecision {

    /** 当前渠道可以继续。 */
    ALLOWED,
    /** 互联网复诊、远程会诊需要预审。 */
    PRECHECK_REQUIRED,
    /** 需要转人工。 */
    NEED_HUMAN,
    /** 当前线上渠道不适用。 */
    UNSUITABLE_FOR_ONLINE
}
