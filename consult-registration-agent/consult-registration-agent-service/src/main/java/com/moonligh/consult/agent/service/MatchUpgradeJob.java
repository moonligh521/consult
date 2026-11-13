package com.moonligh.consult.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.xxl.job.core.handler.annotation.XxlJob;

/**
 * 调度中心扫 delay：满 1 分钟提一档。任务名 matchUpgradeJob。
 */
@Component
public class MatchUpgradeJob {

    private static final Logger log = LoggerFactory.getLogger(MatchUpgradeJob.class);

    private final ConsultationService consultations;

    public MatchUpgradeJob(ConsultationService consultations) {
        this.consultations = consultations;
    }

    @XxlJob("matchUpgradeJob")
    public void matchUpgradeJob() {
        int n = consultations.drainMatchQueue();
        if (n > 0) {
            log.info("提档派单 {} 笔", n);
        }
    }
}
