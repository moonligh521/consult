package com.moonligh.consult.agent.config;

import java.util.Arrays;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.moonligh.consult.agent.domain.department.DepartmentCatalog;
import com.moonligh.consult.agent.domain.triage.ComplaintModel;
import com.moonligh.consult.agent.infrastructure.model.DeepSeekComplaintModel;
import com.moonligh.consult.agent.infrastructure.model.ModelGateway;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * 大模型网关与主诉模型装配。prod 下缺密钥直接起不来。
 */
@Configuration
@EnableConfigurationProperties(ModelProperties.class)
public class ModelConfiguration {

    @Bean
    public ModelGateway modelGateway(
            ModelProperties properties,
            ObjectProvider<StringRedisTemplate> redis,
            MeterRegistry meters,
            Environment environment) {
        boolean prod = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (prod && (properties.getApiKey() == null || properties.getApiKey().isBlank())) {
            throw new IllegalStateException("prod 环境必须配置 model.api-key");
        }
        return new ModelGateway(properties, redis.getIfAvailable(), meters);
    }

    @Bean
    public ComplaintModel complaintModel(ModelGateway gateway, DepartmentCatalog catalog) {
        return new DeepSeekComplaintModel(gateway, catalog);
    }
}
