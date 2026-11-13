package com.moonligh.benefit.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.moonligh.benefit.domain.BenefitLine;
import com.moonligh.benefit.domain.Issue;
import com.moonligh.benefit.domain.Issued;
import com.moonligh.benefit.domain.Product;
import com.moonligh.benefit.domain.Quote;
import com.moonligh.benefit.domain.Reserve;
import com.moonligh.benefit.service.BenefitService;

/** 渠道发卡，问诊下单预占。请求头带 X-Benefit-Token。 */
@RestController
@RequestMapping("/api/benefit")
public class BenefitController {

    private final BenefitService benefits;

    public BenefitController(BenefitService benefits) {
        this.benefits = benefits;
    }

    @PostMapping("/products")
    public void define(@RequestBody Product request) {
        benefits.define(request);
    }

    @PostMapping("/cards")
    public Issued issue(@RequestBody Issue request) {
        return benefits.issue(request);
    }

    @GetMapping("/accounts/{accountId}")
    public List<BenefitLine> account(@PathVariable String accountId) {
        return benefits.account(accountId);
    }

    @PostMapping("/holds")
    public Quote reserve(@RequestBody Reserve request) {
        return benefits.reserve(request);
    }

    @PostMapping("/holds/{orderId}/confirm")
    public void confirm(@PathVariable String orderId) {
        benefits.confirm(orderId);
    }

    @PostMapping("/holds/{orderId}/release")
    public void release(@PathVariable String orderId) {
        benefits.release(orderId);
    }

    @PostMapping("/holds/{orderId}/restore")
    public void restore(@PathVariable String orderId) {
        benefits.restore(orderId);
    }
}
