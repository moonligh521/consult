package com.moonligh.consult.agent.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 派医生队：按二级科+档位、支付时间排序。实现可以是内存或 Redis ZSET。
 */
public interface MatchBroker {

    /** 入本档队，并登记 1 分钟后提档（已是专家则不再登记）。 */
    void enqueue(MatchQueue.Entry entry, Duration upgradeAfter);

    /** 弹出该科该档支付最早的一单。空队返回 null。 */
    MatchQueue.Entry pop(String specialty, int grade);

    /** 占坑失败时按原支付时间放回队头。 */
    void restore(MatchQueue.Entry entry);

    /** 已派上或已结束：从本档、提档队、元数据里删掉。 */
    void complete(String orderId);

    /** 队里还有这单（对账时已在队则不再入，避免把提档时间清掉）。 */
    boolean contains(String orderId);

    /**
     * 对账用：已有元数据则按当前档补回档位队（不改 delay），否则新入队。
     * 返回队里那条（含已提档后的 queuedGrade）。
     */
    MatchQueue.Entry ensure(MatchQueue.Entry entry, Duration upgradeAfter);

    /**
     * 取出并删除已经等满 1 分钟的单（延迟集合 score ≤ now）。
     * limit ≤ 0 表示这一分钟到期的全部拿走。
     */
    List<MatchQueue.Entry> popDueUpgrades(Instant now, int limit);

    /** 挪到高一档，分数仍是原支付时间。 */
    void promote(MatchQueue.Entry entry);
}
