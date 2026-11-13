package com.moonligh.consult.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 科室目录缓存。延时双删的间隔要略大于一次读请求的耗时。
 */
@ConfigurationProperties(prefix = "department.cache")
public class DepartmentProperties {

    /** Redis key。全进程共用一份快照。 */
    private String redisKey = "consult:dept:catalog";
    /** Redis TTL，双删漏掉时的兜底。 */
    private long ttlSeconds = 600;
    /** 进程内快照存活时间。 */
    private long localTtlMs = 30_000;
    /** 第二次删缓存的延迟。 */
    private long doubleDeleteDelayMs = 1000;

    public String getRedisKey() {
        return redisKey;
    }

    public void setRedisKey(String redisKey) {
        this.redisKey = redisKey;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public long getLocalTtlMs() {
        return localTtlMs;
    }

    public void setLocalTtlMs(long localTtlMs) {
        this.localTtlMs = localTtlMs;
    }

    public long getDoubleDeleteDelayMs() {
        return doubleDeleteDelayMs;
    }

    public void setDoubleDeleteDelayMs(long doubleDeleteDelayMs) {
        this.doubleDeleteDelayMs = doubleDeleteDelayMs;
    }
}
