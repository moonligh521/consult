package com.moonligh.consult.agent.infrastructure.pay;

import com.moonligh.consult.agent.domain.pay.PayCommand;
import com.moonligh.consult.agent.domain.pay.PaymentCashier;
import com.moonligh.consult.agent.domain.pay.PaymentTicket;

/** 演示收银。不调用微信、支付宝或银联。 */
public final class DemoPaymentCashier implements PaymentCashier {

    @Override
    public PaymentTicket collect(PayCommand command) {
        String channel = command == null || command.channel() == null || command.channel().isBlank()
                ? "demo"
                : command.channel();
        return new PaymentTicket(true, channel, "");
    }
}
