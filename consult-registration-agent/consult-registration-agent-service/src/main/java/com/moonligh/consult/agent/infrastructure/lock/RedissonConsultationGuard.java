package com.moonligh.consult.agent.infrastructure.lock;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.moonligh.consult.agent.service.ConsultationGuard;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Redisson 分布式锁实现。不等待：拿不到说明同一会话已有请求在跑，直接告诉用户稍候。
 * 租期固定、不续期，进程挂了到期自动释放；租期要大于一次请求最坏耗时（模型排队 + 调用 + 派单）。
 * Redis 不可用时放行，退回只靠行锁串行。
 */
public class RedissonConsultationGuard implements ConsultationGuard {

    private static final Logger log = LoggerFactory.getLogger(RedissonConsultationGuard.class);

    private final RedissonClient redisson;
    private final long leaseMs;
    private final MeterRegistry meters;

    public RedissonConsultationGuard(RedissonClient redisson, Duration lease, MeterRegistry meters) {
        this.redisson = redisson;
        this.leaseMs = lease.toMillis();
        this.meters = meters;
    }

    @Override
    public <T> T exclusive(String consultationId, Supplier<T> action) {
        if (consultationId == null || consultationId.isBlank()) {
            return action.get();
        }
        RLock lock;
        boolean locked;
        try {
            lock = redisson.getLock("consult:inflight:" + consultationId);
            locked = lock.tryLock(0, leaseMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.CONSULT_IN_PROGRESS);
        } catch (RuntimeException error) {
            log.warn("会话单飞锁不可用，退回行锁串行 type={}", error.getClass().getSimpleName());
            count("bypass");
            return action.get();
        }
        if (!locked) {
            count("rejected");
            throw new BusinessException(ErrorCode.CONSULT_IN_PROGRESS);
        }
        count("acquired");
        try {
            return action.get();
        } finally {
            unlock(lock);
        }
    }

    private void unlock(RLock lock) {
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (RuntimeException error) {
            log.warn("释放会话单飞锁失败，等租期到期 type={}", error.getClass().getSimpleName());
        }
    }

    private void count(String outcome) {
        meters.counter("consult_inflight_guard_total", "outcome", outcome).increment();
    }
}
