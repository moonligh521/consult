package com.moonligh.consult.identity.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.moonligh.consult.identity.infrastructure.persistence.StaffSchemaMapper;

/** 已有工作人员表补医生绑定列。新建库由 schema.sql 带上。 */
@Component
@Order(1)
public class StaffSchemaGuard implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StaffSchemaGuard.class);

    private final StaffSchemaMapper schema;

    public StaffSchemaGuard(StaffSchemaMapper schema) {
        this.schema = schema;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer exists = schema.countDoctorIdColumn();
        if (exists == null || exists == 0) {
            schema.addDoctorIdColumn();
        }
        log.info("工作人员医生绑定列已就绪");
    }
}
