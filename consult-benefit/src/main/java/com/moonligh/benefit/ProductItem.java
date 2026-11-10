package com.moonligh.benefit;

import java.math.BigDecimal;

/** 卡种上的一项。P1–P4 是四档问诊次数，CREDIT 是可抵四档的金额。 */
public record ProductItem(String itemCode, BigDecimal quantity) {
}
