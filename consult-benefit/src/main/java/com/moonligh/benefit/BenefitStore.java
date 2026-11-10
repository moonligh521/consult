package com.moonligh.benefit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 账本存储。内存账没有事务，整段预占由 BenefitBook 加锁。
 * 数据库账本在一个事务里用行锁，不同账号可以同时预占。
 */
interface BenefitStore {

    /** 为真时账本把一次预占整段串行。数据库实现返回假。 */
    default boolean serializesCaller() {
        return true;
    }

    void saveProduct(Product product);

    Product findProduct(String code);

    Issued findBySource(String channel, String sourceRef);

    void insertCard(NewCard card);

    Hold findHold(String orderId);

    void insertHold(Hold hold);

    void replaceHold(Hold hold);

    void deleteHold(String orderId);

    void updateStatus(String orderId, int status);

    List<Bucket> lockAccount(String accountId, Instant now);

    boolean take(String cardId, String itemCode, BigDecimal amount);

    void giveBack(String cardId, String itemCode, BigDecimal amount);

    List<BenefitLine> lines(String accountId, Instant now);
}

record NewCard(
        String id,
        String accountId,
        String productCode,
        String channel,
        String sourceRef,
        Instant validFrom,
        Instant validTo,
        List<Balance> balances) {
}

record Balance(String itemCode, BigDecimal remaining) {
}

record Bucket(String cardId, Instant validTo, String itemCode, BigDecimal remaining) {
}

record Hold(
        String orderId,
        String accountId,
        String cardId,
        String itemCode,
        int holdCount,
        BigDecimal holdAmount,
        BigDecimal cashDue,
        BigDecimal catalogAmount,
        int status) {
}
