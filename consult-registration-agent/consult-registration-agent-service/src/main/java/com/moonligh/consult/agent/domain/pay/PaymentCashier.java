package com.moonligh.consult.agent.domain.pay;

/**
 * 确认收款。demo 直接成功。
 * 支付宝、微信、银联在独立支付服务里。这里只发起收款并拿回拉起参数。
 * 真实渠道先返回调起参数，验签回调之后才把订单改成已支付。
 */
public interface PaymentCashier {

    PaymentTicket collect(PayCommand command);
}
