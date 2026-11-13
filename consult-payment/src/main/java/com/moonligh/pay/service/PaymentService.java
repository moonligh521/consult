package com.moonligh.pay.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import com.moonligh.pay.PayProperties;
import com.moonligh.pay.domain.Channels;
import com.moonligh.pay.domain.PayCommand;
import com.moonligh.pay.domain.PayException;
import com.moonligh.pay.domain.PayOrder;
import com.moonligh.pay.domain.PayOrderStore;
import com.moonligh.pay.domain.PayRequest;
import com.moonligh.pay.domain.PayView;
import com.moonligh.pay.infrastructure.channel.AlipayCashier;
import com.moonligh.pay.infrastructure.channel.BankCashier;
import com.moonligh.pay.infrastructure.channel.WechatCashier;
import com.moonligh.pay.infrastructure.notify.BusinessNotifier;

/** 下单、验过的到账、查询。渠道差异留在三个收银台里。 */
@Service
@EnableConfigurationProperties(PayProperties.class)
public class PaymentService {

    private final PayProperties properties;
    private final PayOrderStore store;
    private final BusinessNotifier notifier;
    private final AlipayCashier alipay;
    private final WechatCashier wechat;
    private final BankCashier bank;

    public PaymentService(PayProperties properties, PayOrderStore store, BusinessNotifier notifier) {
        this.properties = properties;
        this.store = store;
        this.notifier = notifier;
        this.alipay = new AlipayCashier(properties);
        this.wechat = new WechatCashier(properties);
        this.bank = new BankCashier(properties);
    }

    public PayView create(PayRequest request) {
        if (request == null
                || blank(request.bizOrderNo())
                || request.amount() == null
                || blank(request.subject())
                || blank(request.notifyUrl())) {
            throw new PayException("PARAM_INVALID");
        }
        String channel = Channels.normalize(request.channel());
        if (!Channels.real(channel)) {
            throw new PayException("PARAM_INVALID");
        }
        if (blank(properties.getNotifyBaseUrl()) || !properties.channelReady(channel)) {
            throw new PayException("PAY_CHANNEL_NOT_READY");
        }
        PayOrder existing = store.find(request.bizOrderNo());
        if (existing != null && existing.paid()) {
            return PayView.of(existing);
        }
        BigDecimal amount = request.amount().setScale(2, RoundingMode.HALF_UP);
        PayOrder pending = new PayOrder(
                request.bizOrderNo().trim(),
                channel,
                amount,
                request.subject().trim(),
                request.notifyUrl().trim(),
                Channels.tradeNo(channel, request.bizOrderNo().trim()),
                0,
                "");
        store.save(pending);
        String payload = prepay(new PayCommand(pending.bizOrderNo(), amount, pending.subject(), channel));
        PayOrder launched = new PayOrder(
                pending.bizOrderNo(),
                channel,
                amount,
                pending.subject(),
                pending.notifyUrl(),
                pending.channelTradeNo(),
                0,
                payload);
        store.save(launched);
        return PayView.of(launched);
    }

    public PayView find(String bizOrderNo) {
        PayOrder order = store.find(bizOrderNo);
        if (order == null) {
            throw new PayException("NOT_FOUND");
        }
        return PayView.of(order);
    }

    /** 渠道验签之后调用。金额不一致或业务方没接住，返回 false，让渠道重试。 */
    public boolean accept(String tradeNo, BigDecimal amount) {
        PayOrder order = store.find(tradeNo);
        if (order == null || amount == null) {
            return false;
        }
        BigDecimal expected = order.amount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal actual = amount.setScale(2, RoundingMode.HALF_UP);
        if (expected.compareTo(actual) != 0) {
            return false;
        }
        if (!order.paid()) {
            store.markPaid(order.bizOrderNo());
        }
        return notifier.notify(order);
    }

    public AlipayCashier alipay() {
        return alipay;
    }

    public WechatCashier wechat() {
        return wechat;
    }

    public BankCashier bank() {
        return bank;
    }

    private String prepay(PayCommand command) {
        return switch (command.channel()) {
            case "alipay" -> alipay.prepay(command);
            case "wechat" -> wechat.prepay(command);
            case "bank" -> bank.prepay(command);
            default -> throw new PayException("PARAM_INVALID");
        };
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
