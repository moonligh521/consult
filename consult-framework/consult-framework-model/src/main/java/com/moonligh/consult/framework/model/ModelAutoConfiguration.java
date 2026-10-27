package com.moonligh.consult.framework.model;

import java.util.Arrays;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * 大模型 HTTP 网关。提示词和解析留在业务服务。prod 缺密钥直接起不来。
 */
@AutoConfiguration
@ConditionalOnClass(ModelGateway.class)
@EnableConfigurationProperties(ModelProperties.class)
public class ModelAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ModelGateway modelGateway(
            ModelProperties properties,
            ObjectProvider<StringRedisTemplate> redis,
            ObjectProvider<ModelQuota> quota,
            ObjectProvider<MeterRegistry> meters,
            Environment environment) {
        boolean prod = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (prod && (properties.getApiKey() == null || properties.getApiKey().isBlank())) {
            throw new IllegalStateException("prod 环境必须配置 model.api-key");
        }
        MeterRegistry registry = meters.getIfAvailable(SimpleMeterRegistry::new);
        return new ModelGateway(properties, redis.getIfAvailable(), registry, quota.getIfAvailable(() -> ModelQuota.NONE));
    }
}
