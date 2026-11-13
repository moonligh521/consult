package com.moonligh.consult.agent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.moonligh.consult.agent.infrastructure.persistence.OrderBenefitSchemaMapper;

/** 已有订单表补权益列。新建库由 schema.sql 带上这些列。 */
@Component
@Order(6)
public class OrderBenefitSchemaGuard implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OrderBenefitSchemaGuard.class);

    private final OrderBenefitSchemaMapper schema;

    public OrderBenefitSchemaGuard(OrderBenefitSchemaMapper schema) {
        this.schema = schema;
    }

    @Override
    public void run(ApplicationArguments args) {
        add("benefit_amount", schema::addBenefitAmount);
        add("benefit_card_id", schema::addBenefitCardId);
        add("benefit_ready", schema::addBenefitReady);
        add("benefit_settled", schema::addBenefitSettled);
        log.info("订单权益列已就绪");
    }

    private void add(String column, Runnable ddl) {
        Integer exists = schema.columnCount(column);
        if (exists == null || exists == 0) {
            ddl.run();
        }
    }
}
