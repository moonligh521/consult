package com.moonligh.consult.agent.infrastructure.model;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonligh.consult.agent.config.ModelProperties;
import com.moonligh.consult.agent.domain.safety.ModelStatus;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * 所有大模型调用的唯一出口：按用途限流（单实例信号量 + 可选全局配额）、熔断、超时，可选 Redis 缓存。不打印原话和密钥。
 * 缓存只存调用方校验过的回复，由调用方 {@link #remember} 写入。
 */
public class ModelGateway {

    private static final Logger log = LoggerFactory.getLogger(ModelGateway.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ModelProperties props;
    private final StringRedisTemplate redis;
    private final MeterRegistry meters;
    private final HttpClient http;
    private final Map<ModelPurpose, Semaphore> limiters = new EnumMap<>(ModelPurpose.class);
    private final Map<ModelPurpose, CircuitBreaker> breakers = new EnumMap<>(ModelPurpose.class);
    private final ModelQuota quota;

    public ModelGateway(ModelProperties props, StringRedisTemplate redis, MeterRegistry meters) {
        this(props, redis, meters, ModelQuota.NONE);
    }

    public ModelGateway(ModelProperties props, StringRedisTemplate redis, MeterRegistry meters, ModelQuota quota) {
        this.props = props;
        this.redis = redis;
        this.meters = meters;
        this.quota = quota == null ? ModelQuota.NONE : quota;
        int connect = Math.max(props.getSafety().getTimeoutMs(), props.getComplaint().getTimeoutMs());
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connect)).build();
        for (ModelPurpose purpose : ModelPurpose.values()) {
            limiters.put(purpose, new Semaphore(Math.max(1, props.lane(purpose).getMaxConcurrency())));
            breakers.put(purpose, CircuitBreaker.standard());
        }
        if (!hasKey()) {
            log.warn("model api-key 为空：安全模型与主诉模型都会回报不可用，按各节点兜底走");
        }
    }

    public boolean hasKey() {
        return props.getApiKey() != null && !props.getApiKey().isBlank();
    }

    public String modelName() {
        return props.getModelName();
    }

    public ModelProperties.Lane lane(ModelPurpose purpose) {
        return props.lane(purpose);
    }

    /** 调一次。cacheKey 非空时先查缓存。 */
    public ModelReply chat(ModelPurpose purpose, String system, String user, String cacheKey, int maxTokens) {
        long started = System.nanoTime();
        if (cacheKey != null) {
            String cached = readCache(cacheKey);
            if (cached != null) {
                count(purpose, ModelStatus.SUCCESS, true);
                return new ModelReply(ModelStatus.SUCCESS, cached, 0, props.getModelName(), true);
            }
        }
        CircuitBreaker breaker = breakers.get(purpose);
        if (breaker.isOpen()) {
            count(purpose, ModelStatus.BREAKER_OPEN, false);
            return ModelReply.failed(ModelStatus.BREAKER_OPEN, 0, props.getModelName());
        }
        Semaphore limiter = limiters.get(purpose);
        ModelProperties.Lane lane = props.lane(purpose);
        boolean acquired;
        try {
            acquired = limiter.tryAcquire(lane.getAcquireWaitMs(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            acquired = false;
        }
        if (!acquired) {
            count(purpose, ModelStatus.BUSY, false);
            return ModelReply.failed(ModelStatus.BUSY, elapsed(started), props.getModelName());
        }
        // 本实例和全局共用一个等待预算，总等待不超过 acquireWaitMs
        ModelQuota.Permit permit = quota.acquire(purpose, lane.getAcquireWaitMs() - elapsed(started));
        if (permit == null) {
            limiter.release();
            count(purpose, ModelStatus.BUSY, false);
            return ModelReply.failed(ModelStatus.BUSY, elapsed(started), props.getModelName());
        }
        if (!breaker.tryPass()) {
            quota.release(permit);
            limiter.release();
            count(purpose, ModelStatus.BREAKER_OPEN, false);
            return ModelReply.failed(ModelStatus.BREAKER_OPEN, 0, props.getModelName());
        }
        try {
            ModelReply reply = send(system, user, lane.getTimeoutMs(), maxTokens, started);
            breaker.record(reply.status() == ModelStatus.SUCCESS);
            count(purpose, reply.status(), false);
            Timer.builder("triage_model_latency")
                    .tag("purpose", purpose.name())
                    .publishPercentiles(0.5, 0.95, 0.99)
                    .register(meters)
                    .record(Duration.ofMillis(reply.latencyMs()));
            return reply;
        } finally {
            quota.release(permit);
            limiter.release();
        }
    }

    /** 调用方校验通过后再写缓存，避免缓存不合格输出。 */
    public void remember(String cacheKey, String content) {
        if (cacheKey == null || content == null || redis == null) {
            return;
        }
        try {
            redis.opsForValue().set(cacheKey, content, Duration.ofHours(props.getCacheTtlHours()));
        } catch (RuntimeException error) {
            log.warn("写模型缓存失败 type={}", error.getClass().getSimpleName());
        }
    }

    public boolean breakerOpen(ModelPurpose purpose) {
        return breakers.get(purpose).isOpen();
    }

    private ModelReply send(String system, String user, int timeoutMs, int maxTokens, long started) {
        if (!hasKey()) {
            return ModelReply.failed(ModelStatus.UNAVAILABLE, 0, props.getModelName());
        }
        try {
            var root = MAPPER.createObjectNode();
            root.put("model", props.getModelName());
            root.put("temperature", 0);
            if (maxTokens > 0) {
                root.put("max_tokens", maxTokens);
            }
            var messages = MAPPER.createArrayNode();
            messages.add(MAPPER.createObjectNode().put("role", "system").put("content", system));
            messages.add(MAPPER.createObjectNode().put("role", "user").put("content", user));
            root.set("messages", messages);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(props.getBaseUrl()) + "/chat/completions"))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Authorization", "Bearer " + props.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(root)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.error("model unavailable status={}", response.statusCode());
                return ModelReply.failed(ModelStatus.UNAVAILABLE, elapsed(started), props.getModelName());
            }
            JsonNode body = MAPPER.readTree(response.body());
            String content = body.path("choices").path(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                return ModelReply.failed(ModelStatus.INVALID_OUTPUT, elapsed(started), props.getModelName());
            }
            return new ModelReply(ModelStatus.SUCCESS, content, elapsed(started), props.getModelName(), false);
        } catch (HttpTimeoutException error) {
            log.error("model timeout");
            return ModelReply.failed(ModelStatus.TIMEOUT, elapsed(started), props.getModelName());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return ModelReply.failed(ModelStatus.UNAVAILABLE, elapsed(started), props.getModelName());
        } catch (Exception error) {
            log.error("model unavailable type={}", error.getClass().getName());
            return ModelReply.failed(ModelStatus.UNAVAILABLE, elapsed(started), props.getModelName());
        }
    }

    private String readCache(String cacheKey) {
        if (redis == null) {
            return null;
        }
        try {
            return redis.opsForValue().get(cacheKey);
        } catch (RuntimeException error) {
            log.warn("读模型缓存失败 type={}", error.getClass().getSimpleName());
            return null;
        }
    }

    private void count(ModelPurpose purpose, ModelStatus status, boolean cached) {
        meters.counter("triage_model_calls_total",
                "purpose", purpose.name(), "status", status.name(), "cached", String.valueOf(cached)).increment();
    }

    private static long elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
