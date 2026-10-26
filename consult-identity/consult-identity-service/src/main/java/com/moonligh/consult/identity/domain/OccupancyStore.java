package com.moonligh.consult.identity.domain;

import java.time.Instant;
import java.util.List;

/** 占坑表。同一订单只占一次。 */
public interface OccupancyStore {

    OccupancyRow find(String orderId);

    void insert(OccupancyRow row);

    boolean markReleased(String orderId, int reason, Instant at);

    boolean updateExpiry(String orderId, Instant expiresAt);

    /** 到期占用，先到期的优先，最多 limit 条。库实现要加 FOR UPDATE SKIP LOCKED。 */
    List<OccupancyRow> claimExpired(Instant now, int limit);
}
