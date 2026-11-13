package com.moonligh.benefit.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record Bucket(String cardId, Instant validTo, String itemCode, BigDecimal remaining) {
}
