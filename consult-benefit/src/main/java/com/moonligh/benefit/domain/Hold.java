package com.moonligh.benefit.domain;

import java.math.BigDecimal;

public record Hold(
        String orderId,
        String accountId,
        String cardId,
        String itemCode,
        int holdCount,
        BigDecimal holdAmount,
        BigDecimal cashDue,
        BigDecimal catalogAmount,
        int status) {
}
