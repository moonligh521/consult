package com.moonligh.consult.agent.api.res;

import java.math.BigDecimal;
import java.time.Instant;

import com.moonligh.consult.agent.api.enums.PayStatus;

/**
 * 已经落下的咨询订单。演示环境支付状态直接是已支付。
 * endedAt / endReason 有值表示本轮问诊已结束（手动或超时），不可再发消息。
 * benefitAmount 是这张权益卡抵掉的金额。payAmount 是还要付的现金。
 */
public record ConsultOrderRes(
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
        /** 本次下单要拉起的渠道。演示和已支付回放为空。全额权益为 benefit。 */
        String payChannel,
        /** 渠道调起参数。支付宝是表单，微信是 code_url，银行是银联网关表单。 */
        String payPayload,
        BigDecimal benefitAmount) {

    public ConsultOrderRes(
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
                consultMinutes, endsAt, endedAt, endReason, null, null, BigDecimal.ZERO);
    }

    public ConsultOrderRes(
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
            String payChannel,
            String payPayload) {
        this(orderId, packageCode, packageName, originAmount, payAmount, discountAmount, payStatus, paidAt, doctor,
                consultMinutes, endsAt, endedAt, endReason, payChannel, payPayload, BigDecimal.ZERO);
    }
}
