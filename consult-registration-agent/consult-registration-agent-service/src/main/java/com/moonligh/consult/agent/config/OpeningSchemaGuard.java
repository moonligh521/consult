package com.moonligh.consult.agent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.moonligh.consult.agent.infrastructure.persistence.OpeningSchemaMapper;

/** 开轮占位表。已有则不动。 */
@Component
@Order(5)
public class OpeningSchemaGuard implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OpeningSchemaGuard.class);

    private final OpeningSchemaMapper schema;

    public OpeningSchemaGuard(OpeningSchemaMapper schema) {
        this.schema = schema;
    }

    @Override
    public void run(ApplicationArguments args) {
        schema.createOpeningTable();
        log.info("开轮占位表已就绪");
    }
}
