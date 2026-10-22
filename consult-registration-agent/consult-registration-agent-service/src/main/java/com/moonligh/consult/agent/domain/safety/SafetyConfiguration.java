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

    /** 启动时加载急症目录。校验失败则进程起不来。 */
    @Bean
    public SafetyCatalog safetyCatalog(SafetyProperties properties, Environment environment) {
        DraftMode mode = DraftMode.valueOf(properties.getCatalog().getDraftMode());
        CatalogLoader.assertDraftModeAllowed(mode, environment.getActiveProfiles());
        return CatalogLoader.load(properties.getCatalog().classpathLocation(), mode);
    }

    /** 规则引擎。无状态，可以单例。 */
    @Bean
    public RuleSafetyEngine ruleSafetyEngine() {
        return new RuleSafetyEngine();
    }

    /** 模型没开就用空实现。默认关闭。 */
    @Bean
    public SignalExtractor signalExtractor(SafetyProperties properties) {
        if (!properties.getModel().isEnabled()) {
            return new DisabledSignalExtractor();
        }
        return new DeepSeekSignalExtractor(properties);
    }

    /** 安全状态计数。不含病情原文。 */
    @Bean
    public SafetyMetrics safetyMetrics() {
        return new SafetyMetrics();
    }
}
