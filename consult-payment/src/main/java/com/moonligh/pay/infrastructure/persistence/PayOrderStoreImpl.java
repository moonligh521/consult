package com.moonligh.pay.infrastructure.persistence;

import java.time.Instant;

import org.springframework.stereotype.Repository;

import com.moonligh.pay.domain.PayOrder;
import com.moonligh.pay.domain.PayOrderStore;

@Repository
public class PayOrderStoreImpl implements PayOrderStore {

    private final PayOrderMapper orders;

    public PayOrderStoreImpl(PayOrderMapper orders) {
        this.orders = orders;
    }

    @Override
    public void save(PayOrder order) {
        orders.upsert(order, Instant.now());
    }

    @Override
    public PayOrder find(String tradeNo) {
        if (tradeNo == null || tradeNo.isBlank()) {
            return null;
        }
        return orders.find(tradeNo);
    }

    @Override
    public void markPaid(String bizOrderNo) {
        orders.markPaid(bizOrderNo, Instant.now());
    }
}
