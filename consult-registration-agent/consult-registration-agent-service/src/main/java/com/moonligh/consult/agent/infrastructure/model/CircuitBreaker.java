package com.moonligh.consult.agent.infrastructure.model;

import java.util.function.LongSupplier;

/**
 * 最近 window 次调用里失败比例到阈值就打开，openMs 内直接拒绝；之后只放一个探测请求。
 */
public final class CircuitBreaker {

    private final boolean[] outcomes;
    private final int minCalls;
    private final double failureRate;
    private final long openMs;
    private final LongSupplier clock;

    private int next;
    private int recorded;
    private long openUntil;
    private boolean probing;

    public CircuitBreaker(int window, int minCalls, double failureRate, long openMs, LongSupplier clock) {
        this.outcomes = new boolean[window];
        this.minCalls = minCalls;
        this.failureRate = failureRate;
        this.openMs = openMs;
        this.clock = clock;
    }

    public static CircuitBreaker standard() {
        return new CircuitBreaker(20, 10, 0.5, 30_000, System::currentTimeMillis);
    }

    /** 能不能发这次请求。半开时只放第一个。 */
    public synchronized boolean tryPass() {
        if (openUntil == 0) {
            return true;
        }
        if (clock.getAsLong() < openUntil) {
            return false;
        }
        if (probing) {
            return false;
        }
        probing = true;
        return true;
    }

    public synchronized void record(boolean success) {
        if (probing) {
            probing = false;
            if (success) {
                openUntil = 0;
                recorded = 0;
                next = 0;
            } else {
                openUntil = clock.getAsLong() + openMs;
            }
            return;
        }
        outcomes[next] = success;
        next = (next + 1) % outcomes.length;
        recorded = Math.min(recorded + 1, outcomes.length);
        if (recorded < minCalls) {
            return;
        }
        int failures = 0;
        for (int i = 0; i < recorded; i++) {
            if (!outcomes[i]) {
                failures++;
            }
        }
        if ((double) failures / recorded >= failureRate) {
            openUntil = clock.getAsLong() + openMs;
        }
    }

    public synchronized boolean isOpen() {
        return openUntil != 0 && clock.getAsLong() < openUntil;
    }
}
