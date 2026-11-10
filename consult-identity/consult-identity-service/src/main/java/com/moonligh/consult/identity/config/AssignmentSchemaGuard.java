package com.moonligh.consult.identity.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 派单账本缺表缺列时补上。已有的不改。
 */
@Component
@Order(5)
public class AssignmentSchemaGuard implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AssignmentSchemaGuard.class);

    private final JdbcTemplate jdbc;

    public AssignmentSchemaGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        column("consult_doctor", "idle_since", "DATETIME(3) NULL COMMENT '进入当前在手单数层的时间'");
        column("consult_doctor", "last_beat", "DATETIME(3) NULL COMMENT '最近一次心跳'");
        column("consult_doctor", "presence", "TINYINT NOT NULL DEFAULT 0 COMMENT '1在线 0下线'");
        jdbc.execute(
                """
                CREATE TABLE IF NOT EXISTS consult_assignment_outbox (
                    order_id         VARCHAR(64)  NOT NULL COMMENT '订单号，幂等键',
                    consultation_id  VARCHAR(64)  NULL     COMMENT '咨询编号',
                    doctor_id        VARCHAR(64)  NOT NULL COMMENT '派到的医生',
                    specialty        VARCHAR(32)  NULL     COMMENT '二级科',
                    schedule_id      VARCHAR(64)  NULL     COMMENT '排班',
                    status           TINYINT      NOT NULL DEFAULT 0 COMMENT '0待发 1已发 2发送中',
                    claim_token      VARCHAR(64)  NULL     COMMENT '本批领取令牌',
                    claimed_at       DATETIME(3)  NULL     COMMENT '领走时间',
                    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '写入时间',
                    sent_at          DATETIME(3)  NULL     COMMENT '发送成功时间',
                    PRIMARY KEY (order_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='派单通知'
                """);
        column("consult_assignment_outbox", "claim_token", "VARCHAR(64) NULL COMMENT '本批领取令牌'");
        column("consult_assignment_outbox", "claimed_at", "DATETIME(3) NULL COMMENT '领走时间'");
        jdbc.execute(
                """
                CREATE TABLE IF NOT EXISTS consult_doctor_inbox (
                    order_id         VARCHAR(64)  NOT NULL COMMENT '订单号，消费幂等键',
                    consultation_id  VARCHAR(64)  NULL     COMMENT '咨询编号',
                    doctor_id        VARCHAR(64)  NOT NULL COMMENT '医生',
                    specialty        VARCHAR(32)  NULL     COMMENT '二级科',
                    schedule_id      VARCHAR(64)  NULL     COMMENT '排班',
                    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '入箱时间',
                    PRIMARY KEY (order_id),
                    KEY idx_consult_doctor_inbox_doctor (doctor_id, created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='医生待接'
                """);
        log.info("派单账本表已就绪");
    }

    private void column(String table, String name, String definition) {
        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                """,
                Integer.class,
                table,
                name);
        if (count != null && count > 0) {
            return;
        }
        jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + name + " " + definition);
        log.info("补列 {}.{}", table, name);
    }
}
