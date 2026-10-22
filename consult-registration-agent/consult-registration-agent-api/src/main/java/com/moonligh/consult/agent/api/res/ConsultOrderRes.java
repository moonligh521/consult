package com.moonligh.consult.agent.api.res;

import java.math.BigDecimal;
import java.time.Instant;

import com.moonligh.consult.agent.api.enums.PayStatus;

/**
 * 已经落下的咨询订单。演示环境支付状态直接是已支付。
 */
public record ConsultOrderRes(
        /** 订单编号。 */
        String orderId,
        /** 档位数字。 */
        int packageCode,
        /** 下单时的档位名称。 */
        String packageName,
        /** 划线价。 */
        BigDecimal originAmount,
        /** 应付金额。 */
        BigDecimal payAmount,
        /** 立减金额。 */
        BigDecimal discountAmount,
        /** 支付状态。当前演示为 2 已支付。 */
        PayStatus payStatus,
        /** 记为已支付的时间。 */
        Instant paidAt) {
}
