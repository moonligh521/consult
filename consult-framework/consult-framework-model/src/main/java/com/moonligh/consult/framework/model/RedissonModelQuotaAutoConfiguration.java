package com.moonligh.consult.framework.model;

import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * 有 Redisson 时挂上全局配额。没有则 {@link ModelAutoConfiguration} 用 {@link ModelQuota#NONE}。
 */
@AutoConfiguration(afterName = "com.moonligh.consult.framework.redis.RedissonAutoConfiguration")
@ConditionalOnClass(RedissonClient.class)
@EnableConfigurationProperties(ModelProperties.class)
public class RedissonModelQuotaAutoConfiguration {

    @Bean
    @ConditionalOnBean(RedissonClient.class)
    @ConditionalOnMissingBean
    public ModelQuota modelQuota(
            RedissonClient redisson,
            ModelProperties properties,
            ObjectProvider<MeterRegistry> meters) {
        return new RedissonModelQuota(redisson, properties, meters.getIfAvailable(SimpleMeterRegistry::new));
    }
}
