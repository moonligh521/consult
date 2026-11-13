package com.moonligh.pay.domain;

import java.math.BigDecimal;

/** 一笔待支付或已支付的收款。业务单号是调用方的订单号。 */
public record PayOrder(
        String bizOrderNo,
        String channel,
        BigDecimal amount,
        String subject,
        String notifyUrl,
        String channelTradeNo,
        int status,
        String payload) {

    public boolean paid() {
        return status == 1;
    }
}
