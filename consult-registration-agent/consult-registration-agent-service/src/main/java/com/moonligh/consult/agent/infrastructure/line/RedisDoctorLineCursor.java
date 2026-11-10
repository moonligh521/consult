package com.moonligh.consult.agent.infrastructure.line;

import java.time.Duration;
import java.util.OptionalInt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import com.moonligh.consult.agent.domain.DoctorLineCursor;

/**
 * 医生消息序号放在 Redis。连不上时 5 秒内不再碰 Redis，轮询改回读库。
 */
public final class RedisDoctorLineCursor implements DoctorLineCursor {

    private static final Logger log = LoggerFactory.getLogger(RedisDoctorLineCursor.class);
    private static final Duration TTL = Duration.ofHours(48);
    private static final long OPEN_MS = 5_000L;
    private static final DefaultRedisScript<Long> ADVANCE = new DefaultRedisScript<>("""
            local cur = redis.call('GET', KEYS[1])
            local nxt = tonumber(ARGV[1])
            if (not cur) or (tonumber(cur) < nxt) then
              redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2])
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private volatile long openUntil;

    public RedisDoctorLineCursor(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public OptionalInt read(String consultationId) {
        if (blank(consultationId) || tripped()) {
            return OptionalInt.empty();
        }
        try {
            String value = redis.opsForValue().get(key(consultationId));
            if (value == null || value.isBlank()) {
                return OptionalInt.empty();
            }
            return OptionalInt.of(Integer.parseInt(value.trim()));
        } catch (RuntimeException error) {
            trip(error);
            return OptionalInt.empty();
        }
    }

    @Override
    public void publish(String consultationId, int seq) {
        if (blank(consultationId) || seq < 0 || tripped()) {
            return;
        }
        try {
            redis.execute(ADVANCE, java.util.List.of(key(consultationId)), Integer.toString(seq), Long.toString(TTL.toSeconds()));
        } catch (RuntimeException error) {
            trip(error);
        }
    }

    private boolean tripped() {
        return System.currentTimeMillis() < openUntil;
    }

    private void trip(RuntimeException error) {
        openUntil = System.currentTimeMillis() + OPEN_MS;
        log.warn("医生消息序号 Redis 不可用，5 秒内改回读库 type={}", error.getClass().getSimpleName());
    }

    private static String key(String consultationId) {
        return "consult:doctor-line:" + consultationId.trim();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
