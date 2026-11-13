package com.moonligh.consult.agent.domain;

/**
 * 最近几小时里已经预占、还需要对一下权益账的订单。
 * paid 为真则确认预占；cancelled 为真则退回。
 */
public record BenefitLedgerRow(String orderId, boolean paid, boolean cancelled) {
}
