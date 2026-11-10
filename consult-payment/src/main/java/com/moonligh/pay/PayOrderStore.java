package com.moonligh.pay;

/** 支付单存储。已支付的行不会被后一次下单改掉。 */
public interface PayOrderStore {

    void save(PayOrder order);

    PayOrder find(String tradeNo);

    void markPaid(String bizOrderNo);
}
