package com.moonligh.consult.agent.domain.pay;

import java.math.BigDecimal;

/**
 * 发给支付服务的收款请求。字段与支付服务的 PayRequest 一致。
 */
public record PaymentCreateReq(
        String bizOrderNo,
        BigDecimal amount,
        String subject,
        String channel,
        String notifyUrl) {
}
