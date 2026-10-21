package com.moonligh.consult.agent.domain;

/**
 * 安全分流命中哪一档。没有命中就是 NONE，继续普通导诊去挂号。
 * 急症优先于转人工。要不要线下就医由线上医生判断，这里不拦截。
 */
public enum SafetyHit {

    NONE,
    URGENT,
    HUMAN
}
