package com.moonligh.consult.identity.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.moonligh.consult.identity.infrastructure.persistence.AssignmentSchemaMapper;

/**
 * 派单账本缺表缺列时补上。已有的不改。
 */
@Component
@Order(5)
public class AssignmentSchemaGuard implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AssignmentSchemaGuard.class);

    private final AssignmentSchemaMapper schema;

    public AssignmentSchemaGuard(AssignmentSchemaMapper schema) {
        this.schema = schema;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensure("consult_doctor", "idle_since", schema::addDoctorIdleSince);
        ensure("consult_doctor", "last_beat", schema::addDoctorLastBeat);
        ensure("consult_doctor", "presence", schema::addDoctorPresence);
        schema.createOutbox();
        ensure("consult_assignment_outbox", "claim_token", schema::addOutboxClaimToken);
        ensure("consult_assignment_outbox", "claimed_at", schema::addOutboxClaimedAt);
        schema.createInbox();
        log.info("派单账本表已就绪");
    }

    private void ensure(String table, String name, Runnable alter) {
        Integer count = schema.countColumn(table, name);
        if (count != null && count > 0) {
            return;
        }
        alter.run();
        log.info("补列 {}.{}", table, name);
    }
}
