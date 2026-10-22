package com.moonligh.consult.agent.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.moonligh.consult.agent.api.enums.PayStatus;
import com.moonligh.consult.agent.api.res.ConsultOrderRes;
import com.moonligh.consult.agent.api.res.PayPackageRes;

/**
 * 一轮咨询上的订单。演示下单后直接记已支付。
 */
public record ConsultOrder(
        String orderId,
        int packageCode,
        String packageName,
        BigDecimal originAmount,
        BigDecimal payAmount,
        BigDecimal discountAmount,
        PayStatus payStatus,
        Instant paidAt) {

    /** 按当时目录价生成一笔已支付订单。 */
    public static ConsultOrder paidOf(PayPackageRes pkg) {
        Instant now = Instant.now();
        return new ConsultOrder(
                UUID.randomUUID().toString(),
                pkg.code(),
                pkg.name(),
                pkg.originPrice(),
                pkg.payPrice(),
                pkg.discount(),
                PayStatus.PAID,
                now);
    }

    /** 给前端的订单画面。 */
    public ConsultOrderRes toRes() {
        return new ConsultOrderRes(
                orderId,
                packageCode,
                packageName,
                originAmount,
                payAmount,
                discountAmount,
                payStatus,
                paidAt);
    }
}
