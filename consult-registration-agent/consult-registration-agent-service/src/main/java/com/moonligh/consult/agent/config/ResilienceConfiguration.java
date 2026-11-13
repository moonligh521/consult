package com.moonligh.consult.agent.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedBulkheadMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 下游身份服务的熔断与舱壁。指标以 resilience4j_* 暴露到 /actuator/prometheus。
 */
@Configuration
public class ResilienceConfiguration {

    public static final String IDENTITY = "identity";

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry(MeterRegistry meters) {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(meters);
        return registry;
    }

    @Bean
    public BulkheadRegistry bulkheadRegistry(MeterRegistry meters) {
        BulkheadRegistry registry = BulkheadRegistry.ofDefaults();
        TaggedBulkheadMetrics.ofBulkheadRegistry(registry).bindTo(meters);
        return registry;
    }

    /** 最近 50 次里失败或慢调用（超过 2 秒）过半就熔断 10 秒，之后放 5 个探测请求。 */
    @Bean
    public CircuitBreaker identityCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker(IDENTITY, CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(50)
                .minimumNumberOfCalls(20)
                .failureRateThreshold(50)
                .slowCallDurationThreshold(Duration.ofSeconds(2))
                .slowCallRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(5)
                .build());
    }

    /** 单实例同时打身份服务的请求上限，排队最多 200ms；按身份服务实例数 × 其连接池估算后配置。 */
    @Bean
    public Bulkhead identityBulkhead(
            BulkheadRegistry registry,
            @Value("${consult.identity.max-concurrency:200}") int maxConcurrency,
            @Value("${consult.identity.max-wait:200ms}") Duration maxWait) {
        return registry.bulkhead(IDENTITY, BulkheadConfig.custom()
                .maxConcurrentCalls(maxConcurrency)
                .maxWaitDuration(maxWait)
                .build());
    }
}
