package com.moonligh.benefit.domain;

import java.time.Instant;
import java.util.List;

public record NewCard(
        String id,
        String accountId,
        String productCode,
        String channel,
        String sourceRef,
        Instant validFrom,
        Instant validTo,
        List<Balance> balances) {
}
