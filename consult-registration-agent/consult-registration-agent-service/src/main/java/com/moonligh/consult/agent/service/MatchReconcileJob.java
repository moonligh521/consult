package com.moonligh.consult.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.xxl.job.core.handler.annotation.XxlJob;

/**
 * 调度中心对账。任务名 matchReconcileJob，建议 10～30 秒一拍。
 */
@Component
public class MatchReconcileJob {

    private static final Logger log = LoggerFactory.getLogger(MatchReconcileJob.class);

    private final ConsultationService consultations;

    public MatchReconcileJob(ConsultationService consultations) {
        this.consultations = consultations;
    }

    @XxlJob("matchReconcileJob")
    public void matchReconcileJob() {
        int n = consultations.reconcileMatchQueue();
        if (n > 0) {
            log.info("对账补派 {} 笔", n);
        }
    }
}
