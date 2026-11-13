package com.moonligh.consult.agent.config;

import java.time.Duration;
import java.util.function.Supplier;

import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.moonligh.consult.agent.service.ConsultationGuard;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.consult.framework.redis.ExclusiveGuard;
import com.moonligh.consult.framework.redis.RedissonExclusiveGuard;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * 同一会话单飞。Redisson 客户端与 ExclusiveGuard 在 platform-redis。
 */
@Configuration
public class RedissonConfiguration {

    /** 租期要盖住一次请求最坏耗时：模型排队 1.5s + 安全模型两次 5s + 主诉 2.8s + 派单 5s。 */
    @Bean
    @ConditionalOnBean(RedissonClient.class)
    public ConsultationGuard consultationGuard(
            RedissonClient redisson,
            @Value("${consult.guard.lease:75s}") Duration lease,
            MeterRegistry meters) {
        ExclusiveGuard exclusive = new RedissonExclusiveGuard(
                redisson,
                "consult:inflight:",
                lease,
                ErrorCode.CONSULT_IN_PROGRESS,
                "consult_inflight_guard_total",
                meters);
        return new ConsultationGuard() {
            @Override
            public <T> T exclusive(String consultationId, Supplier<T> action) {
                return exclusive.exclusive(consultationId, action);
            }
        };
    }
}
