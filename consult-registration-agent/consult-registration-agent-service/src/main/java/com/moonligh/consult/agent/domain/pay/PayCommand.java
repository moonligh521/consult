package com.moonligh.consult.agent.domain.pay;

import java.math.BigDecimal;

/**
 * 向渠道下单的入参。金额以目录价为准，不采用前端传来的数字。
 */
public record PayCommand(
        String consultationId,
        String orderId,
        BigDecimal amountYuan,
        String subject,
        String channel) {
}
