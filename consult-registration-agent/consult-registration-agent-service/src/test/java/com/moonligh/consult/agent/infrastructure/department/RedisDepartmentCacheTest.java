package com.moonligh.consult.agent.infrastructure.department;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonligh.consult.agent.domain.department.DepartmentSeed;
import com.moonligh.consult.agent.domain.department.DepartmentSnapshot;

class RedisDepartmentCacheTest {

    @Test
    void freshLocalSnapshotDoesNotTouchRedis() {
        StringRedisTemplate redis = failingRedis();
        RedisDepartmentCache cache = cache(redis, 60_000);
        DepartmentSnapshot expected = DepartmentSeed.load();
        cache.put(expected);

        DepartmentSnapshot actual = cache.load(() -> {
            fail("fresh local snapshot must not hit source");
            return null;
        });

        assertSame(expected, actual);
    }

    @Test
    void expiredLocalSnapshotSurvivesRedisTimeoutWithoutDatabaseReload() throws Exception {
        StringRedisTemplate redis = failingRedis();
        RedisDepartmentCache cache = cache(redis, 0);
        DepartmentSnapshot expected = DepartmentSeed.load();
        cache.put(expected);
        Thread.sleep(2);

        DepartmentSnapshot actual = cache.load(() -> {
            fail("Redis timeout with last-known-good snapshot must not reload source");
            return null;
        });

        assertSame(expected, actual);
    }

    @SuppressWarnings("unchecked")
    private static StringRedisTemplate failingRedis() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenThrow(new IllegalStateException("redis timeout"));
        return redis;
    }

    private static RedisDepartmentCache cache(StringRedisTemplate redis, long localTtlMs) {
        return new RedisDepartmentCache(
                redis,
                new ObjectMapper().findAndRegisterModules(),
                "test:catalog",
                Duration.ofMinutes(10),
                localTtlMs);
    }
}
