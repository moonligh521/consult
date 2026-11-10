package com.moonligh.pay;

import java.math.BigDecimal;

/** 向渠道下单时用的单号、金额和标题。 */
public record PayCommand(String orderId, BigDecimal amountYuan, String subject, String channel) {
}
