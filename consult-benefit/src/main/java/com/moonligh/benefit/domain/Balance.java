package com.moonligh.benefit.domain;

import java.math.BigDecimal;

public record Balance(String itemCode, BigDecimal remaining) {
}
