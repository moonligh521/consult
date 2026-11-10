package com.moonligh.consult.agent.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

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
        String endReason,
        BigDecimal benefitAmount,
        String benefitCardId,
        boolean benefitReady) {

    /** 旧构造不带权益。权益金额为 0，尚未向权益服务预占。 */
    public ConsultOrder(
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
        this(orderId, packageCode, packageName, originAmount, payAmount, discountAmount, payStatus, paidAt, doctor,
                consultMinutes, endsAt, endedAt, endReason, BigDecimal.ZERO, null, false);
    }

    // endsAt=计划到期；endedAt/endReason=实际结束。有 endedAt 就不能再改时长，占坑也不再被超时扫描。

    /** 按当时目录价生成一笔已支付订单。 */
    public static ConsultOrder paidOf(PayPackageRes pkg) {
        return paidOf(pkg, ConsultMinutes.DEFAULT);
    }

    public static ConsultOrder paidOf(PayPackageRes pkg, int minutes) {
        Instant now = Instant.now();
        int length = ConsultMinutes.of(minutes);
        return new ConsultOrder(
                TimeOrderedIds.next(),
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

    /** 真实渠道先落待支付。回调验签通过后再 markPaid。 */
    public static ConsultOrder pendingOf(PayPackageRes pkg, int minutes) {
        return new ConsultOrder(
                TimeOrderedIds.next(),
                pkg.code(),
                pkg.name(),
                pkg.originPrice(),
                pkg.payPrice(),
                pkg.discount(),
                PayStatus.PENDING,
                null,
                null,
                ConsultMinutes.of(minutes),
                null,
                null,
                null);
    }

    public ConsultOrder markPaid(Instant at) {
        Instant when = at == null ? Instant.now() : at;
        int length = ConsultMinutes.of(consultMinutes);
        return new ConsultOrder(
                orderId,
                packageCode,
                packageName,
                originAmount,
                payAmount,
                discountAmount,
                PayStatus.PAID,
                when,
                doctor,
                length,
                when.plus(Duration.ofMinutes(length)),
                null,
                null,
                benefitAmount,
                benefitCardId,
                benefitReady);
    }

    /** 预占结果写回。cash 是还要付的现金，covered 是权益抵掉的金额。 */
    public ConsultOrder applyBenefit(BigDecimal cash, BigDecimal covered, String cardId) {
        return new ConsultOrder(
                orderId,
                packageCode,
                packageName,
                originAmount,
                cash,
                discountAmount,
                payStatus,
                paidAt,
                doctor,
                consultMinutes,
                endsAt,
                endedAt,
                endReason,
                covered == null ? BigDecimal.ZERO : covered,
                cardId,
                true);
    }

    /** 预下单失败被取消后，同一档可以再拉起一次。 */
    public ConsultOrder reopen() {
        BigDecimal catalog = originAmount.subtract(discountAmount);
        return new ConsultOrder(
                orderId,
                packageCode,
                packageName,
                originAmount,
                catalog,
                discountAmount,
                PayStatus.PENDING,
                null,
                null,
                consultMinutes,
                null,
                null,
                null,
                BigDecimal.ZERO,
                null,
                false);
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
                endReason,
                benefitAmount,
                benefitCardId,
                benefitReady);
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
                endReason,
                benefitAmount,
                benefitCardId,
                benefitReady);
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
                code,
                benefitAmount,
                benefitCardId,
                benefitReady);
    }

    public boolean isEnded() {
        return endedAt != null;
    }

    /** 给前端的订单画面。 */
    public ConsultOrderRes toRes() {
        BigDecimal covered = benefitAmount == null ? BigDecimal.ZERO : benefitAmount;
        String channel = covered.signum() > 0 && payAmount.signum() == 0 ? "benefit" : null;
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
                endReason,
                channel,
                null,
                covered);
    }
}
