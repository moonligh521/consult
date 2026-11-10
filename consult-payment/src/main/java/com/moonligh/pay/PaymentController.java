package com.moonligh.pay;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任何业务都用同一套接口。请求头带 X-Pay-Token。
 * POST /api/payments 发起收款，GET /api/payments/{bizOrderNo} 查询。
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping
    public PayView create(@RequestBody PayRequest request) {
        return payments.create(request);
    }

    @GetMapping("/{bizOrderNo}")
    public PayView query(@PathVariable String bizOrderNo) {
        return payments.find(bizOrderNo);
    }
}
