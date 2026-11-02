package com.moonligh.consult.agent.domain.safety;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 安全分流计数，不含病情原文。
 */
public class SafetyMetrics {

    private final ConcurrentMap<String, AtomicLong> counters = new ConcurrentHashMap<>();

    /** 计数加一。没有这个名字就先建一个。 */
    public void increment(String name) {
        counters.computeIfAbsent(name, key -> new AtomicLong()).incrementAndGet();
    }

    /** 读取当前计数。没有过就是 0。 */
    public long count(String name) {
        AtomicLong value = counters.get(name);
        return value == null ? 0L : value.get();
    }
}
