package com.moonligh.consult.framework.model;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RPermitExpirableSemaphore;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * 基于 Redisson 可过期信号量的全局配额。许可带租期，实例宕机没来得及归还的许可到期自动回收。
 * Redis 出错后 5 秒内不再访问，直接放行给单实例信号量，避免每次调用都卡在 Redis 超时上。
 */
public class RedissonModelQuota implements ModelQuota {

    private static final Logger log = LoggerFactory.getLogger(RedissonModelQuota.class);
    private static final long SKIP_AFTER_ERROR_MS = 5_000;
    /** 租期比单次调用超时多留的余量，覆盖网络抖动和 GC 停顿。 */
    private static final long LEASE_SLACK_MS = 5_000;

    private final Map<ModelPurpose, RPermitExpirableSemaphore> semaphores = new EnumMap<>(ModelPurpose.class);
    private final Map<ModelPurpose, Integer> permits = new EnumMap<>(ModelPurpose.class);
    private final Map<ModelPurpose, Long> leaseMs = new EnumMap<>(ModelPurpose.class);
    private final Set<ModelPurpose> initialized = ConcurrentHashMap.newKeySet();
    private final MeterRegistry meters;
    private volatile long skipUntil;

    public RedissonModelQuota(RedissonClient redisson, ModelProperties props, MeterRegistry meters) {
        this(redisson, props, meters, "consult:model:quota:");
    }

    public RedissonModelQuota(RedissonClient redisson, ModelProperties props, MeterRegistry meters, String keyPrefix) {
        this.meters = meters;
        for (ModelPurpose purpose : ModelPurpose.values()) {
            ModelProperties.Lane lane = props.lane(purpose);
            if (lane.getGlobalMaxConcurrency() <= 0) {
                continue;
            }
            String name = keyPrefix + purpose.name().toLowerCase(Locale.ROOT);
            semaphores.put(purpose, redisson.getPermitExpirableSemaphore(name));
            permits.put(purpose, lane.getGlobalMaxConcurrency());
            leaseMs.put(purpose, lane.getTimeoutMs() + LEASE_SLACK_MS);
        }
    }

    @Override
    public Permit acquire(ModelPurpose purpose, long waitMs) {
        RPermitExpirableSemaphore semaphore = semaphores.get(purpose);
        if (semaphore == null) {
            return Permit.UNLIMITED;
        }
        if (System.currentTimeMillis() < skipUntil) {
            count(purpose, "bypass");
            return Permit.UNLIMITED;
        }
        try {
            ensurePermits(purpose, semaphore);
            String id = semaphore.tryAcquire(Math.max(0, waitMs), leaseMs.get(purpose), TimeUnit.MILLISECONDS);
            if (id == null) {
                count(purpose, "denied");
                return null;
            }
            count(purpose, "granted");
            return new Permit(purpose, id);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return null;
        } catch (RuntimeException error) {
            skipUntil = System.currentTimeMillis() + SKIP_AFTER_ERROR_MS;
            log.warn("模型全局配额不可用，暂按单实例限流放行 type={}", error.getClass().getSimpleName());
            count(purpose, "bypass");
            return Permit.UNLIMITED;
        }
    }

    @Override
    public void release(Permit permit) {
        if (permit == null || permit.id() == null) {
            return;
        }
        RPermitExpirableSemaphore semaphore = semaphores.get(permit.purpose());
        if (semaphore == null) {
            return;
        }
        try {
            semaphore.tryRelease(permit.id());
        } catch (RuntimeException error) {
            log.warn("归还模型全局配额失败，等租期到期回收 type={}", error.getClass().getSimpleName());
        }
    }

    /** setPermits 按差值调整总量，各实例配置相同则重复调用无副作用；配置改大改小重启即生效。 */
    private void ensurePermits(ModelPurpose purpose, RPermitExpirableSemaphore semaphore) {
        if (!initialized.add(purpose)) {
            return;
        }
        try {
            semaphore.setPermits(permits.get(purpose));
        } catch (RuntimeException error) {
            initialized.remove(purpose);
            throw error;
        }
    }

    private void count(ModelPurpose purpose, String outcome) {
        meters.counter("triage_model_quota_total", "purpose", purpose.name(), "outcome", outcome).increment();
    }
}
