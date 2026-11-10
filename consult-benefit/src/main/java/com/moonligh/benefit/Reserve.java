package com.moonligh.benefit;

import java.math.BigDecimal;

/** 下单预占。catalogAmount 是这一档的目录实付价。 */
public record Reserve(String accountId, String orderId, int packageCode, BigDecimal catalogAmount) {
}
