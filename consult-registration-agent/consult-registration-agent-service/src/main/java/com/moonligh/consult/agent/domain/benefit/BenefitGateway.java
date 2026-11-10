package com.moonligh.consult.agent.domain.benefit;

import java.math.BigDecimal;
import java.util.List;

import com.moonligh.consult.agent.api.res.BenefitItemRes;

/** 挂号服务看到的权益卡。没开时 enabled 为 false，下单按原价走。 */
public interface BenefitGateway {

    boolean enabled();

    BenefitQuote reserve(String accountId, String orderId, int packageCode, BigDecimal catalogPay);

    void confirm(String orderId);

    void release(String orderId);

    void restore(String orderId);

    List<BenefitItemRes> account(String accountId);
}
