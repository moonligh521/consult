package com.moonligh.consult.agent.api.res;

import java.math.BigDecimal;
import java.time.Instant;

/** 账号下一张权益卡的一项剩余。 */
public record BenefitItemRes(
        String cardId,
        String productCode,
        String itemCode,
        BigDecimal remaining,
        Instant validTo) {
}
