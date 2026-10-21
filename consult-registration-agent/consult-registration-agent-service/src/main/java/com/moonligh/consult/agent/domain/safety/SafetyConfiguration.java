package com.moonligh.consult.agent.domain.safety;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import com.moonligh.consult.agent.infrastructure.langchain.DeepSeekSignalExtractor;

/**
 * 安全分流 Spring 装配。
 */
@Configuration
@EnableConfigurationProperties(SafetyProperties.class)
public class SafetyConfiguration {

    @Bean
    public SafetyCatalog safetyCatalog(SafetyProperties properties, Environment environment) {
        DraftMode mode = DraftMode.valueOf(properties.getCatalog().getDraftMode());
        CatalogLoader.assertDraftModeAllowed(mode, environment.getActiveProfiles());
        return CatalogLoader.load(properties.getCatalog().classpathLocation(), mode);
    }

    @Bean
    public RuleSafetyEngine ruleSafetyEngine() {
        return new RuleSafetyEngine();
    }

    @Bean
    public SignalExtractor signalExtractor(SafetyProperties properties) {
        if (!properties.getModel().isEnabled()) {
            return new DisabledSignalExtractor();
        }
        return new DeepSeekSignalExtractor(properties);
    }

    @Bean
    public SafetyMetrics safetyMetrics() {
        return new SafetyMetrics();
    }
}
