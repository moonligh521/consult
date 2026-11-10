package com.moonligh.benefit;

import java.math.BigDecimal;

/** 预占结果。cardId 为空表示这单不占额度，现金就是目录实付价。 */
public record Quote(
        String orderId,
        String cardId,
        String itemCode,
        BigDecimal benefitAmount,
        BigDecimal cashDue) {
}
