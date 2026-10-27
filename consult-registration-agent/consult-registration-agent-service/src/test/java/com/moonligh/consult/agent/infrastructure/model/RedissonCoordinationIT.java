package com.moonligh.consult.agent.infrastructure.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import com.moonligh.consult.agent.config.ModelProperties;
import com.moonligh.consult.agent.infrastructure.lock.RedissonConsultationGuard;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * 连真实 Redis 验全局配额与会话单飞。键带随机前缀，跑完删掉。
 * mvn -Dit.redis=true -Dit.redis.host=... -Dit.redis.password=... -Dtest=RedissonCoordinationIT test
 */
@EnabledIfSystemProperty(named = "it.redis", matches = "true")
class RedissonCoordinationIT {

    private static RedissonClient redisson;
    private static final String PREFIX = "it:" + UUID.randomUUID() + ":";

    @BeforeAll
    static void connect() {
        Config config = new Config();
        var server = config.useSingleServer().setAddress(
                "redis://" + System.getProperty("it.redis.host", "127.0.0.1") + ":"
                        + System.getProperty("it.redis.port", "6379"));
        String password = System.getProperty("it.redis.password", "");
        if (!password.isBlank()) {
            server.setPassword(password);
        }
        redisson = Redisson.create(config);
    }

    @AfterAll
    static void cleanup() {
        if (redisson != null) {
            redisson.getKeys().deleteByPattern(PREFIX + "*");
            redisson.shutdown();
        }
    }

    @Test
    void globalQuotaCapsAcrossHoldersAndReleases() {
        ModelProperties props = new ModelProperties();
        props.getSafety().setGlobalMaxConcurrency(2);
        props.getSafety().setTimeoutMs(1000);
        RedissonModelQuota quota = new RedissonModelQuota(redisson, props, new SimpleMeterRegistry(), PREFIX);
        // 两个实例各一份客户端对象，模拟跨实例共享同一配额
        RedissonModelQuota otherInstance = new RedissonModelQuota(redisson, props, new SimpleMeterRegistry(), PREFIX);

        ModelQuota.Permit first = quota.acquire(ModelPurpose.SAFETY, 100);
        ModelQuota.Permit second = otherInstance.acquire(ModelPurpose.SAFETY, 100);
        assertNotNull(first.id());
        assertNotNull(second.id());
        assertNull(quota.acquire(ModelPurpose.SAFETY, 200));

        otherInstance.release(second);
        ModelQuota.Permit third = quota.acquire(ModelPurpose.SAFETY, 200);
        assertNotNull(third.id());
        quota.release(first);
        quota.release(third);
        assertEquals(ModelQuota.Permit.UNLIMITED, quota.acquire(ModelPurpose.COMPLAINT, 0));
    }

    @Test
    void secondRequestOnSameConsultationIsRejectedWhileFirstRuns() throws Exception {
        RedissonConsultationGuard guard =
                new RedissonConsultationGuard(redisson, Duration.ofSeconds(10), new SimpleMeterRegistry());
        String id = PREFIX + "c1";
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        CompletableFuture<String> running = CompletableFuture.supplyAsync(() -> guard.exclusive(id, () -> {
            entered.countDown();
            try {
                finish.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
            return "done";
        }));
        entered.await(5, TimeUnit.SECONDS);

        BusinessException rejected = assertThrows(BusinessException.class, () -> guard.exclusive(id, () -> "dup"));
        assertEquals(ErrorCode.CONSULT_IN_PROGRESS, rejected.getErrorCode());
        assertEquals("other", guard.exclusive(PREFIX + "c2", () -> "other"));

        finish.countDown();
        assertEquals("done", running.get(5, TimeUnit.SECONDS));
        assertEquals("again", guard.exclusive(id, () -> "again"));
    }
}
