package com.moonligh.consult.agent.api.res;

import java.math.BigDecimal;
import java.time.Instant;

import com.moonligh.consult.agent.api.enums.PayStatus;

/**
 * 已经落下的咨询订单。演示环境支付状态直接是已支付。
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
        Instant endedAt) {
}
