package com.moonligh.consult.agent.domain.pay;

/**
 * 收银结果。paid 为真才允许把订单写成已支付。clientPayload 留给微信或支付宝调起参数，演示渠道为空。
 */
public record PaymentTicket(boolean paid, String channel, String clientPayload) {
}
