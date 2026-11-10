package com.moonligh.pay;

import java.math.BigDecimal;

/** 任意业务发起收款。notifyUrl 是这笔单付完后要通知的地址。 */
public record PayRequest(
        String bizOrderNo,
        BigDecimal amount,
        String subject,
        String channel,
        String notifyUrl) {
}
