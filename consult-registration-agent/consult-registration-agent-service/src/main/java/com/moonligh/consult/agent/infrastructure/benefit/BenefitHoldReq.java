package com.moonligh.consult.agent.infrastructure.benefit;

import java.math.BigDecimal;

/**
 * 权益预占请求。字段与权益服务的 Reserve 一致：catalogAmount 是这一档的目录实付价。
 */
public record BenefitHoldReq(String accountId, String orderId, int packageCode, BigDecimal catalogAmount) {
}
