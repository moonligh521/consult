package com.moonligh.benefit;

import java.math.BigDecimal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 建表，并写入四张示例卡种。已有同编码的卡种不覆盖。 */
@Component
@Order(1)
public class BenefitSchema implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BenefitSchema.class);

    private final JdbcTemplate jdbc;
    private final BenefitService benefits;

    public BenefitSchema(JdbcTemplate jdbc, BenefitService benefits) {
        this.jdbc = jdbc;
        this.benefits = benefits;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbc.execute(
                """
                CREATE TABLE IF NOT EXISTS benefit_product (
                    code       VARCHAR(32)  NOT NULL,
                    name       VARCHAR(64)  NOT NULL,
                    valid_days INT          NOT NULL,
                    status     TINYINT      NOT NULL,
                    PRIMARY KEY (code)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        jdbc.execute(
                """
                CREATE TABLE IF NOT EXISTS benefit_product_item (
                    product_code VARCHAR(32)    NOT NULL,
                    item_code    VARCHAR(16)    NOT NULL,
                    quantity     DECIMAL(12, 2) NOT NULL,
                    PRIMARY KEY (product_code, item_code)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        jdbc.execute(
                """
                CREATE TABLE IF NOT EXISTS benefit_card (
                    id             VARCHAR(64) NOT NULL,
                    account_id     VARCHAR(64) NOT NULL,
                    product_code   VARCHAR(32) NOT NULL,
                    source_channel VARCHAR(32) NOT NULL,
                    source_ref     VARCHAR(64) NOT NULL,
                    status         TINYINT     NOT NULL,
                    valid_from     DATETIME(3) NOT NULL,
                    valid_to       DATETIME(3) NOT NULL,
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_benefit_card_source (source_channel, source_ref),
                    KEY idx_benefit_card_account (account_id, valid_to)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        jdbc.execute(
                """
                CREATE TABLE IF NOT EXISTS benefit_balance (
                    card_id   VARCHAR(64)    NOT NULL,
                    item_code VARCHAR(16)    NOT NULL,
                    remaining DECIMAL(12, 2) NOT NULL,
                    PRIMARY KEY (card_id, item_code)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        jdbc.execute(
                """
                CREATE TABLE IF NOT EXISTS benefit_hold (
                    order_id       VARCHAR(64)    NOT NULL,
                    account_id     VARCHAR(64)    NOT NULL,
                    card_id        VARCHAR(64)    NOT NULL,
                    item_code      VARCHAR(16)    NOT NULL,
                    hold_count     INT            NOT NULL,
                    hold_amount    DECIMAL(12, 2) NOT NULL,
                    cash_due       DECIMAL(12, 2) NOT NULL,
                    catalog_amount DECIMAL(12, 2) NOT NULL,
                    status         TINYINT        NOT NULL,
                    PRIMARY KEY (order_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
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
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM benefit_product WHERE code = ?", Integer.class, code);
        if (count != null && count > 0) {
            return;
        }
        benefits.define(new Product(code, name, days, items));
    }
}
