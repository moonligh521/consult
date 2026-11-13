package com.moonligh.consult.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.xxl.job.core.handler.annotation.XxlJob;

/**
 * 权益确认或退回失败后补对。调度中心任务名 benefitSettleJob。
 * 本进程每 30 秒也对一次，调度中心打不到这台机器时账仍能对齐。重复确认和退回都是空操作。
 */
@Component
public class BenefitSettleJob {

    private static final Logger log = LoggerFactory.getLogger(BenefitSettleJob.class);

    private final ConsultationService consultations;

    public BenefitSettleJob(ConsultationService consultations) {
        this.consultations = consultations;
    }

    @XxlJob("benefitSettleJob")
    public void benefitSettleJob() {
        runQuietly();
    }

    @Scheduled(initialDelay = 30_000, fixedDelay = 30_000)
    public void scheduled() {
        runQuietly();
    }

    private void runQuietly() {
        try {
            int settled = consultations.reconcileBenefits();
            if (settled > 0) {
                log.info("权益对账 {} 笔", settled);
            }
        } catch (RuntimeException error) {
            log.warn("权益对账失败 type={}", error.getClass().getSimpleName());
        }
    }
}
