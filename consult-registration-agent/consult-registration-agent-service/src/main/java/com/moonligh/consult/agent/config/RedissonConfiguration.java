package com.moonligh.consult.agent.config;

import java.time.Duration;

import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.moonligh.consult.agent.infrastructure.lock.RedissonConsultationGuard;
import com.moonligh.consult.agent.service.ConsultationGuard;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * 同一会话单飞。Redisson 客户端在 consult-framework-redis。
 */
@Configuration
public class RedissonConfiguration {

    /** 租期要盖住一次请求最坏耗时：模型排队 1.5s + 安全模型两次 5s + 主诉 2.8s + 派单 5s。 */
    @Bean
    @ConditionalOnBean(RedissonClient.class)
    public ConsultationGuard consultationGuard(
            RedissonClient redisson,
            @Value("${consult.guard.lease:20s}") Duration lease,
            MeterRegistry meters) {
        return new RedissonConsultationGuard(redisson, lease, meters);
    }
}
