package com.moonligh.consult.agent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 已有订单表补权益列。新建库由 schema.sql 带上这些列。 */
@Component
@Order(6)
public class OrderBenefitSchemaGuard implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OrderBenefitSchemaGuard.class);

    private final JdbcTemplate jdbc;

    public OrderBenefitSchemaGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        add("benefit_amount",
                "ALTER TABLE consult_order ADD COLUMN benefit_amount DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '权益抵掉的金额' AFTER discount_amount");
        add("benefit_card_id",
                "ALTER TABLE consult_order ADD COLUMN benefit_card_id VARCHAR(64) NULL COMMENT '本次使用的权益卡' AFTER benefit_amount");
        add("benefit_ready",
                "ALTER TABLE consult_order ADD COLUMN benefit_ready TINYINT NOT NULL DEFAULT 0 COMMENT '1表示已经向权益服务预占过' AFTER benefit_card_id");
        add("benefit_settled",
                "ALTER TABLE consult_order ADD COLUMN benefit_settled TINYINT NOT NULL DEFAULT 0 COMMENT '1表示预占已经确认或退回' AFTER benefit_ready");
        log.info("订单权益列已就绪");
    }

    private void add(String column, String ddl) {
        Integer exists = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'consult_order' AND column_name = ?
                """,
                Integer.class,
                column);
        if (exists == null || exists == 0) {
            jdbc.execute(ddl);
        }
    }
}
