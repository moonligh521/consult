package com.moonligh.pay.controller;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.moonligh.pay.infrastructure.channel.WechatCashier;
import com.moonligh.pay.service.PaymentService;

import jakarta.servlet.http.HttpServletRequest;

/** 支付宝、微信、银联的异步通知。不认登录令牌，只认渠道验签。 */
@RestController
@RequestMapping("/api/pay/notify")
public class ChannelNotifyController {

    private final PaymentService payments;

    public ChannelNotifyController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping(value = "/alipay", produces = "text/plain;charset=UTF-8")
    public String alipay(HttpServletRequest request) {
        if (!payments.alipay().verify(form(request))) {
            return "failure";
        }
        String status = request.getParameter("trade_status");
        if (!"TRADE_SUCCESS".equals(status) && !"TRADE_FINISHED".equals(status)) {
            return "success";
        }
        return payments.accept(request.getParameter("out_trade_no"), decimal(request.getParameter("total_amount")))
                ? "success"
                : "failure";
    }

    @PostMapping("/wechat")
    public Map<String, String> wechat(HttpServletRequest request) throws Exception {
        String body = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String plain = payments.wechat().verifiedPlain(
                request.getHeader("Wechatpay-Serial"),
                body,
                request.getHeader("Wechatpay-Signature"),
                request.getHeader("Wechatpay-Nonce"),
                request.getHeader("Wechatpay-Timestamp"));
        if (plain == null || !"SUCCESS".equals(WechatCashier.tradeState(plain))) {
            return plain == null ? WechatCashier.fail() : WechatCashier.success();
        }
        int fen = WechatCashier.amountFen(plain);
        if (fen < 0) {
            return WechatCashier.fail();
        }
        boolean ok = payments.accept(WechatCashier.outTradeNo(plain), BigDecimal.valueOf(fen).movePointLeft(2));
        return ok ? WechatCashier.success() : WechatCashier.fail();
    }

    @PostMapping(value = "/bank", produces = "text/plain;charset=UTF-8")
    public String bank(HttpServletRequest request) {
        Map<String, String> notice = form(request);
        if (!payments.bank().verify(notice)) {
            return "fail";
        }
        if (!"00".equals(notice.get("respCode"))) {
            return "ok";
        }
        String fen = notice.get("txnAmt");
        if (fen == null || fen.isBlank()) {
            return "fail";
        }
        return payments.accept(notice.get("orderId"), new BigDecimal(fen).movePointLeft(2)) ? "ok" : "fail";
    }

    private static Map<String, String> form(HttpServletRequest request) {
        Map<String, String> notice = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                notice.put(key, values[0]);
            }
        });
        return notice;
    }

    private static BigDecimal decimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return new BigDecimal(value);
    }
}
