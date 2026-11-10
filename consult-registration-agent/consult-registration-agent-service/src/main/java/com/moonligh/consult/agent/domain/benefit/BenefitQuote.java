package com.moonligh.consult.agent.domain.benefit;

import java.math.BigDecimal;

/** 权益服务给出的这一单结果。cardId 为空表示没有可占的额度。 */
public record BenefitQuote(String cardId, BigDecimal benefitAmount, BigDecimal cashDue) {
}
