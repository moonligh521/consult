package com.moonligh.consult.agent.api.res;

import java.math.BigDecimal;
import java.util.List;

/**
 * 给下单页看的一档咨询。价格是演示价，不代表真实收费。
 */
public record PayPackageRes(
        /** 档位数字。1 普通 2 极速 3 三甲 4 权威。 */
        int code,
        /** 档位名称。 */
        String name,
        /** 角标，没有就空串。 */
        String badge,
        /** 一行说明。 */
        String summary,
        /** 划线价。 */
        BigDecimal originPrice,
        /** 应付价。 */
        BigDecimal payPrice,
        /** 立减金额。 */
        BigDecimal discount,
        /** 权益要点。 */
        List<String> highlights,
        /** 咨询规则。 */
        String rule,
        /** 出诊时间；没有就空串。 */
        String schedule,
        /** 退款规则。 */
        String refund) {
}
