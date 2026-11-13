package com.moonligh.pay.domain;

import java.math.BigDecimal;

/** 给调用方的收款结果。payload 是支付宝表单、微信 code_url 或银联表单。 */
public record PayView(
        String bizOrderNo,
        String channel,
        BigDecimal amount,
        boolean paid,
        String payload) {

    public static PayView of(PayOrder order) {
        return new PayView(
                order.bizOrderNo(),
                order.channel(),
                order.amount(),
                order.paid(),
                order.payload() == null ? "" : order.payload());
    }
}
