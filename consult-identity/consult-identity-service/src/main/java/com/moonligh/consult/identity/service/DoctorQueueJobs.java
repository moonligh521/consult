package com.moonligh.consult.identity.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.moonligh.consult.identity.config.DoctorLoadRebuilder;
import com.moonligh.consult.identity.store.DoctorStore;
import com.xxl.job.core.handler.annotation.XxlJob;

/**
 * 出站重投、医生队列按库重铺、心跳超时下线。调度中心不可达时，派单成交不依赖这些任务。
 */
@Component
public class DoctorQueueJobs {

    private static final Logger log = LoggerFactory.getLogger(DoctorQueueJobs.class);

    private final AssignmentPublisher publisher;
    private final DoctorLoadRebuilder rebuilder;
    private final DoctorStore doctors;
    private final boolean demoHeartbeat;

    public DoctorQueueJobs(
            AssignmentPublisher publisher,
            DoctorLoadRebuilder rebuilder,
            DoctorStore doctors,
            @Value("${consult.doctor.demo-heartbeat:false}") boolean demoHeartbeat) {
        this.publisher = publisher;
        this.rebuilder = rebuilder;
        this.doctors = doctors;
        this.demoHeartbeat = demoHeartbeat;
    }

    @XxlJob("assignmentOutboxJob")
    public void assignmentOutboxJob() {
        int n = publisher.drain();
        if (n > 0) {
            log.info("重投派单通知 {} 条", n);
        }
    }

    @XxlJob("doctorLoadRebuildJob")
    public void doctorLoadRebuildJob() {
        int n = rebuilder.rebuild();
        if (n > 0) {
            log.info("按库重建医生队列 {} 人", n);
        }
    }

    @XxlJob("doctorPresenceSweepJob")
    public void doctorPresenceSweepJob() {
        if (demoHeartbeat) {
            return;
        }
        int n = doctors.offlineStale();
        if (n > 0) {
            log.info("心跳超时下线 {} 人", n);
        }
    }
}
