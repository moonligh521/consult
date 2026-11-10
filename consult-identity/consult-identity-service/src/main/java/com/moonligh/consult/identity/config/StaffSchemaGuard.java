package com.moonligh.consult.identity.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 已有工作人员表补医生绑定列。新建库由 schema.sql 带上。 */
@Component
@Order(1)
public class StaffSchemaGuard implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StaffSchemaGuard.class);

    private final JdbcTemplate jdbc;

    public StaffSchemaGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer exists = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'consult_staff' AND column_name = 'doctor_id'
                """,
                Integer.class);
        if (exists == null || exists == 0) {
            jdbc.execute(
                    "ALTER TABLE consult_staff ADD COLUMN doctor_id VARCHAR(64) NULL COMMENT '绑定的医生。空则不能回复、不能心跳' AFTER name");
        }
        log.info("工作人员医生绑定列已就绪");
    }
}
