package com.moonligh.consult.identity.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.moonligh.consult.identity.domain.OccupancyService;
import com.xxl.job.core.handler.annotation.XxlJob;

/**
 * 超时占坑只走调度中心，没有 @Scheduled。任务名 occupancyReapJob，每次最多释放 200 条。
 */
@Component
public class OccupancyReapJob {

    private static final Logger log = LoggerFactory.getLogger(OccupancyReapJob.class);

    private final OccupancyService occupancy;

    public OccupancyReapJob(OccupancyService occupancy) {
        this.occupancy = occupancy;
    }

    @XxlJob("occupancyReapJob")
    @Transactional
    public void occupancyReapJob() {
        // 和 claimExpired 的 SKIP LOCKED 必须在同一事务里，否则锁马上就放了
        int n = occupancy.reapBatch();
        if (n > 0) {
            log.info("超时释放占坑 {} 笔", n);
        }
    }
}
