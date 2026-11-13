package com.moonligh.benefit.infrastructure.persistence;

import java.math.BigDecimal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.moonligh.benefit.domain.Product;
import com.moonligh.benefit.domain.ProductItem;
import com.moonligh.benefit.service.BenefitService;

/** 建表，并写入四张示例卡种。已有同编码的卡种不覆盖。 */
@Component
@Order(1)
public class BenefitSchema implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BenefitSchema.class);

    private final BenefitMapper benefits;
    private final BenefitService service;

    public BenefitSchema(BenefitMapper benefits, BenefitService service) {
        this.benefits = benefits;
        this.service = service;
    }

    @Override
    public void run(ApplicationArguments args) {
        benefits.ensureProduct();
        benefits.ensureProductItem();
        benefits.ensureCard();
        benefits.ensureBalance();
        benefits.ensureHold();
        seed("TRIAL", "体验卡", 30, List.of(new ProductItem("P1", new BigDecimal("1"))));
        seed("CHRONIC_BASE", "慢病基础卡", 365, List.of(
                new ProductItem("P1", new BigDecimal("12")),
                new ProductItem("P2", new BigDecimal("4"))));
        seed("CHRONIC_PLUS", "慢病升级卡", 365, List.of(
                new ProductItem("P1", new BigDecimal("12")),
                new ProductItem("P3", new BigDecimal("4")),
                new ProductItem("P4", new BigDecimal("1"))));
        seed("CREDIT_100", "抵扣金卡", 180, List.of(new ProductItem("CREDIT", new BigDecimal("100.00"))));
        log.info("权益卡表已就绪");
    }

    private void seed(String code, String name, int days, List<ProductItem> items) {
        if (benefits.countProduct(code) > 0) {
            return;
        }
        service.define(new Product(code, name, days, items));
    }
}
