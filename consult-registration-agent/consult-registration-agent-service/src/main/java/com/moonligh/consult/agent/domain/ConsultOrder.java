package com.moonligh.consult.agent.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.moonligh.consult.agent.api.enums.PayStatus;
import com.moonligh.consult.agent.api.res.AssignedDoctorRes;
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
        Instant paidAt,
        AssignedDoctorRes doctor,
        int consultMinutes,
        Instant endsAt,
        Instant endedAt,
        String endReason) {
    // endsAt=计划到期；endedAt/endReason=实际结束。有 endedAt 就不能再改时长，占坑也不再被超时扫描。

    /** 按当时目录价生成一笔已支付订单。 */
    public static ConsultOrder paidOf(PayPackageRes pkg) {
        return paidOf(pkg, ConsultMinutes.DEFAULT);
    }

    public static ConsultOrder paidOf(PayPackageRes pkg, int minutes) {
        Instant now = Instant.now();
        int length = ConsultMinutes.of(minutes);
        return new ConsultOrder(
                UUID.randomUUID().toString(),
                pkg.code(),
                pkg.name(),
                pkg.originPrice(),
                pkg.payPrice(),
                pkg.discount(),
                PayStatus.PAID,
                now,
                null,
                length,
                now.plus(Duration.ofMinutes(length)),
                null,
                null);
    }

    public ConsultOrder withDoctor(AssignedDoctorRes assigned) {
        return new ConsultOrder(
                orderId,
                packageCode,
                packageName,
                originAmount,
                payAmount,
                discountAmount,
                payStatus,
                paidAt,
                assigned,
                consultMinutes,
                endsAt,
                endedAt,
                endReason);
    }

    public ConsultOrder withMinutes(int minutes) {
        int length = ConsultMinutes.of(minutes);
        Instant now = Instant.now();
        return new ConsultOrder(
                orderId,
                packageCode,
                packageName,
                originAmount,
                payAmount,
                discountAmount,
                payStatus,
                paidAt,
                doctor,
                length,
                now.plus(Duration.ofMinutes(length)),
                endedAt,
                endReason);
    }

    /** 落结束态。reason：ENDED / TIMEOUT / CANCEL。 */
    public ConsultOrder ended(Instant at, String reason) {
        String code = reason == null || reason.isBlank() ? "ENDED" : reason.trim().toUpperCase();
        return new ConsultOrder(
                orderId,
                packageCode,
                packageName,
                originAmount,
                payAmount,
                discountAmount,
                payStatus,
                paidAt,
                doctor,
                consultMinutes,
                endsAt,
                at,
                code);
    }

    public boolean isEnded() {
        return endedAt != null;
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
                paidAt,
                doctor,
                consultMinutes,
                endsAt,
                endedAt,
                endReason);
    }
}
