package com.moonligh.consult.identity.domain;

import java.time.Instant;

/**
 * 一笔订单对医生图文坑的占用。释放幂等。
 */
public record OccupancyRow(
        String orderId,
        String doctorId,
        String scheduleId,
        int status,
        Instant occupiedAt,
        Instant expiresAt,
        Instant releasedAt) {

    /** 1占用中 2用户结束 3取消 4超时。结束过就不能再减 in_flight。 */
    public static final int ACTIVE = 1;
    public static final int ENDED = 2;
    public static final int CANCELLED = 3;
    public static final int TIMEOUT = 4;

    public OccupancyRow released(int reason, Instant at) {
        return new OccupancyRow(orderId, doctorId, scheduleId, reason, occupiedAt, expiresAt, at);
    }

    public OccupancyRow withExpiresAt(Instant next) {
        return new OccupancyRow(orderId, doctorId, scheduleId, status, occupiedAt, next, releasedAt);
    }

    public static int reasonCode(String reason) {
        if ("TIMEOUT".equalsIgnoreCase(reason)) {
            return TIMEOUT;
        }
        if ("CANCEL".equalsIgnoreCase(reason)) {
            return CANCELLED;
        }
        return ENDED;
    }
}
