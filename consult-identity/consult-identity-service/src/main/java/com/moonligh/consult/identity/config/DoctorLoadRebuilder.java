package com.moonligh.consult.identity.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.moonligh.consult.identity.domain.DoctorGrade;
import com.moonligh.consult.identity.domain.load.DoctorLoadPool;
import com.moonligh.consult.identity.infrastructure.persistence.DoctorStore;
import com.moonligh.consult.identity.infrastructure.persistence.DoctorStore.PresentDoctor;

/**
 * Redis 医生队列丢了就按库里的在途和空闲时间重铺。MySQL 仍是账本。
 */
@Component
@Order(30)
public class DoctorLoadRebuilder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DoctorLoadRebuilder.class);

    private final DoctorStore doctors;
    private final ObjectProvider<DoctorLoadPool> loadPools;

    public DoctorLoadRebuilder(DoctorStore doctors, ObjectProvider<DoctorLoadPool> loadPools) {
        this.doctors = doctors;
        this.loadPools = loadPools;
    }

    @Override
    public void run(ApplicationArguments args) {
        int n = rebuild();
        if (n > 0) {
            log.info("按库重建医生队列 {} 人", n);
        }
    }

    public int rebuild() {
        DoctorLoadPool loadPool = loadPools.getIfAvailable();
        if (loadPool == null) {
            log.warn("医生队列未接上 Redis，跳过按库重建");
            return 0;
        }
        int n = 0;
        long now = System.currentTimeMillis();
        List<PresentDoctor> present = doctors.listPresent();
        try {
            for (PresentDoctor row : present) {
                long idle = row.idleSinceMillis() > 0 ? row.idleSinceMillis() : now;
                loadPool.online(
                        row.id(), row.specialty(), DoctorGrade.ofTitle(row.title()),
                        row.inFlight(), row.concurrentLimit(), idle);
                n++;
            }
        } catch (RuntimeException error) {
            log.warn("医生队列重建中断，已重建 {} 人 type={}", n, error.getClass().getSimpleName());
        }
        return n;
    }
}
