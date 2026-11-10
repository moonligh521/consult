package com.moonligh.consult.agent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 开轮占位表。已有则不动。 */
@Component
@Order(5)
public class OpeningSchemaGuard implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OpeningSchemaGuard.class);

    private final JdbcTemplate jdbc;

    public OpeningSchemaGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbc.execute(
                """
                CREATE TABLE IF NOT EXISTS consult_opening (
                    account_id      VARCHAR(64) NOT NULL COMMENT '账号',
                    text_hash       CHAR(64)    NOT NULL COMMENT '原话摘要',
                    consultation_id VARCHAR(64) NOT NULL COMMENT '这一轮咨询',
                    created_at      DATETIME(3) NOT NULL COMMENT '占住时间',
                    PRIMARY KEY (account_id, text_hash)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='8秒内同一句只开一轮'
                """);
        log.info("开轮占位表已就绪");
    }
}
