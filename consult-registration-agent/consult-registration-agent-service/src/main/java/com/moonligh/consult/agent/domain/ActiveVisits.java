package com.moonligh.consult.agent.domain;

/**
 * 进行中的问诊。同一个账号的同一位就诊人同时只能有一笔，不同就诊人可以并行。
 */
public interface ActiveVisits {

    /**
     * 除本轮以外，这位就诊人是否已经有未结束的问诊。
     */
    boolean busy(String accountId, String patientId, String consultationId);

    /**
     * 支付后占住。本轮重复调用仍成功。已被别的咨询占住返回 false。
     */
    boolean claim(String accountId, String patientId, String consultationId);

    /**
     * 问诊结束时释放。只释放本轮占住的那一行。
     */
    void release(String accountId, String patientId, String consultationId);
}
