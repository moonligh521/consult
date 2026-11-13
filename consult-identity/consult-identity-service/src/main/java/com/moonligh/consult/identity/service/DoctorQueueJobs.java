package com.moonligh.consult.identity.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.moonligh.consult.identity.config.DoctorLoadRebuilder;
import com.xxl.job.core.handler.annotation.XxlJob;

/**
 * 出站重投、医生队列按库重铺。医生在线只认接诊台按钮，不在这里改。
 */
@Component
public class DoctorQueueJobs {

    private static final Logger log = LoggerFactory.getLogger(DoctorQueueJobs.class);

    private final AssignmentPublisher publisher;
    private final DoctorLoadRebuilder rebuilder;

    public DoctorQueueJobs(AssignmentPublisher publisher, DoctorLoadRebuilder rebuilder) {
        this.publisher = publisher;
        this.rebuilder = rebuilder;
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
        // 在线状态只认接诊台的上线、下线。心跳中断不再把医生改成下线。
    }
}
