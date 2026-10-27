package com.moonligh.consult.agent.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.moonligh.consult.agent.config.InquiryProperties;
import com.moonligh.consult.agent.domain.MatchBroker;
import com.moonligh.consult.agent.domain.MatchQueue;

/**
 * 支付入队、放号出队、满 1 分钟提一档。队在 Redis/内存有序集合里，不扫订单表。
 */
public class MatchDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MatchDispatcher.class);
    /** 0：把 delay 里已经到期的（满 1 分钟）一次取完并删掉。 */
    public static final int UPGRADE_BATCH = 0;

    private final MatchBroker broker;
    private final Occupier occupier;
    private final Duration upgradeAfter;
    private final Clock clock;

    public MatchDispatcher(MatchBroker broker, Occupier occupier, InquiryProperties inquiry, Clock clock) {
        this.broker = broker;
        this.occupier = occupier;
        this.upgradeAfter = inquiry == null ? Duration.ofMinutes(1) : inquiry.getMatchUpgradeAfter();
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    /** 落单后入本档队，若自己是队头且有空坑则立刻占。 */
    public void offer(MatchQueue.Entry entry) {
        if (entry == null || entry.orderId() == null) {
            return;
        }
        broker.enqueue(entry, upgradeAfter);
        dispatch(entry.specialty(), entry.queuedGrade() <= 0 ? entry.packageCode() : entry.queuedGrade());
    }

    /** 启动时把未派上的单灌回队列，再按科/档各出一次队。 */
    public void restoreAll(Iterable<MatchQueue.Entry> entries) {
        if (entries == null) {
            return;
        }
        LinkedHashSet<String> lanes = new LinkedHashSet<>();
        for (MatchQueue.Entry entry : entries) {
            if (entry == null || entry.orderId() == null) {
                continue;
            }
            broker.enqueue(entry, upgradeAfter);
            int grade = entry.queuedGrade() <= 0 ? entry.packageCode() : entry.queuedGrade();
            lanes.add((entry.specialty() == null ? "" : entry.specialty()) + "\t" + grade);
        }
        for (String lane : lanes) {
            int split = lane.lastIndexOf('\t');
            dispatch(lane.substring(0, split), Integer.parseInt(lane.substring(split + 1)));
        }
    }

    /** 该科该档有医生空出来：只消化这一档的队头。 */
    public int onSlotFreed(String specialty, int grade) {
        return dispatch(specialty, grade);
    }

    /** 把到期的队头提一档，再按新档出队。 */
    public int promoteDue() {
        Instant now = clock.instant();
        int assigned = 0;
        for (MatchQueue.Entry entry : broker.popDueUpgrades(now, UPGRADE_BATCH)) {
            broker.promote(entry);
            MatchQueue.Entry promoted = entry.promoted();
            assigned += dispatch(promoted.specialty(), promoted.queuedGrade());
        }
        return assigned;
    }

    private int dispatch(String specialty, int grade) {
        if (specialty == null || specialty.isBlank() || grade < 1 || grade > 4) {
            return 0;
        }
        int assigned = 0;
        while (true) {
            MatchQueue.Entry entry = broker.pop(specialty, grade);
            if (entry == null) {
                return assigned;
            }
            try {
                if (occupier.occupy(entry)) {
                    broker.complete(entry.orderId());
                    assigned++;
                    continue;
                }
                broker.restore(entry);
                return assigned;
            } catch (RuntimeException error) {
                broker.restore(entry);
                log.warn("派单出队失败 specialty={} grade={} type={}",
                        specialty, grade, error.getClass().getSimpleName());
                return assigned;
            }
        }
    }

    public interface Occupier {
        /** 占到医生返回 true；本档没坑返回 false，调用方把单放回队头。 */
        boolean occupy(MatchQueue.Entry entry);
    }
}
