package com.moonligh.pay.infrastructure.persistence;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 启动时补齐收款表。库 consult_pay 需要事先建好。 */
@Component
public class PaySchema implements ApplicationRunner {

    private final PayOrderMapper orders;

    public PaySchema(PayOrderMapper orders) {
        this.orders = orders;
    }

    @Override
    public void run(ApplicationArguments args) {
        orders.ensureTable();
    }
}
