package com.moonligh.benefit.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 一张卡上某一项还剩多少。 */
public record BenefitLine(
        String cardId,
        String productCode,
        String itemCode,
        BigDecimal remaining,
        Instant validTo) {
}
