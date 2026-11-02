package com.moonligh.consult.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.moonligh.consult.framework.job.XxlJobProperties;
import com.xxl.job.core.handler.annotation.XxlJob;

/**
 * 旁路话术评分。XXL 开启时只走调度中心；否则按 interval-ms 自跑。
 */
@Component
@ConditionalOnProperty(prefix = "consult.observability.judge", name = "enabled", havingValue = "true")
public class TurnJudgeJob {

    private static final Logger log = LoggerFactory.getLogger(TurnJudgeJob.class);

    private final TurnJudgeService service;
    private final XxlJobProperties xxl;

    public TurnJudgeJob(TurnJudgeService service, XxlJobProperties xxl) {
        this.service = service;
        this.xxl = xxl;
    }

    @XxlJob("turnJudgeJob")
    public void turnJudgeJob() {
        runQuietly();
    }

    @Scheduled(
            initialDelayString = "${consult.observability.judge.interval-ms:900000}",
            fixedDelayString = "${consult.observability.judge.interval-ms:900000}")
    public void scheduled() {
        if (xxl != null && xxl.isEnabled()) {
            return;
        }
        runQuietly();
    }

    private void runQuietly() {
        try {
            service.runOnce();
        } catch (RuntimeException error) {
            log.warn("turn judge job type={}", error.getClass().getSimpleName());
        }
    }
}
