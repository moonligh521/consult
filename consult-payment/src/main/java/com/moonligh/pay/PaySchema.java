package com.moonligh.pay;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 启动时补齐收款表。库 consult_pay 需要事先建好。 */
@Component
public class PaySchema implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    public PaySchema(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS pay_order (
                    biz_order_no VARCHAR(64) NOT NULL,
                    channel VARCHAR(16) NOT NULL,
                    amount DECIMAL(12,2) NOT NULL,
                    subject VARCHAR(128) NOT NULL,
                    notify_url VARCHAR(512) NOT NULL,
                    channel_trade_no VARCHAR(64) NOT NULL,
                    status TINYINT NOT NULL,
                    payload MEDIUMTEXT NULL,
                    created_at DATETIME(3) NOT NULL,
                    paid_at DATETIME(3) NULL,
                    PRIMARY KEY (biz_order_no),
                    KEY idx_pay_order_trade (channel_trade_no)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
    }
}
