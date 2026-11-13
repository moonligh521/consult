package com.moonligh.consult.agent.controller;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.moonligh.consult.agent.config.PayProperties;
import com.moonligh.consult.agent.service.ConsultationService;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.consult.framework.Result;

/**
 * 支付服务验签之后通知这里。金额和目录价一致才改已支付并派医生。
 */
@RestController
@RequestMapping("/api/pay/callback")
public class PayCallbackController {

    private final ConsultationService consultations;
    private final PayProperties pay;

    public PayCallbackController(ConsultationService consultations, PayProperties pay) {
        this.consultations = consultations;
        this.pay = pay;
    }

    @PostMapping
    public Result<Void> paid(
            @RequestHeader(value = "X-Pay-Token", required = false) String token,
            @RequestBody PaidNotice notice) {
        if (!sameToken(token)) {
            return Result.fail(ErrorCode.LOGIN_REQUIRED);
        }
        if (notice == null || notice.bizOrderNo() == null || notice.amount() == null) {
            return Result.fail(ErrorCode.PAYMENT_PENDING);
        }
        boolean ok = consultations.confirmPaid(notice.bizOrderNo(), new BigDecimal(notice.amount()));
        return ok ? Result.ok() : Result.fail(ErrorCode.PAYMENT_PENDING);
    }

    private boolean sameToken(String given) {
        String expected = pay.getToken() == null ? "" : pay.getToken().trim();
        byte[] left = expected.getBytes(StandardCharsets.UTF_8);
        byte[] right = given == null ? new byte[0] : given.getBytes(StandardCharsets.UTF_8);
        return left.length > 0 && left.length == right.length && MessageDigest.isEqual(left, right);
    }

    public record PaidNotice(String bizOrderNo, String amount, String channel) {
    }
}
