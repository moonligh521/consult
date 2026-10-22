package com.moonligh.consult.agent.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import com.moonligh.consult.agent.domain.safety.CatalogLoader;
import com.moonligh.consult.agent.domain.safety.DisabledSignalExtractor;
import com.moonligh.consult.agent.domain.safety.DraftMode;
import com.moonligh.consult.agent.domain.safety.RuleSafetyEngine;
import com.moonligh.consult.agent.domain.safety.SafetyCatalog;
import com.moonligh.consult.agent.domain.safety.SafetyGate;
import com.moonligh.consult.agent.domain.safety.SafetyMetrics;
import com.moonligh.consult.agent.domain.safety.SignalExtractor;
import com.moonligh.consult.agent.infrastructure.langchain.DeepSeekSignalExtractor;
import com.moonligh.consult.agent.infrastructure.safety.JdbcSafetyCatalogReleaseStore;

/**
 * 安全分流 Spring 装配。业务规则仍在 domain.safety。
 */
@Configuration
@EnableConfigurationProperties(SafetyProperties.class)
public class SafetyConfiguration {

    /** 启动时加载急症目录。校验失败则进程起不来。 */
    @Bean
    public JdbcSafetyCatalogReleaseStore jdbcSafetyCatalogReleaseStore(JdbcTemplate jdbc) {
        return new JdbcSafetyCatalogReleaseStore(jdbc);
    }

    @Bean
    public SafetyCatalog safetyCatalog(
            SafetyProperties properties,
            Environment environment,
            JdbcSafetyCatalogReleaseStore releases) {
        DraftMode mode = DraftMode.valueOf(properties.getCatalog().getDraftMode());
        CatalogLoader.assertDraftModeAllowed(mode, environment.getActiveProfiles());
        String location = properties.getCatalog().classpathLocation();
        SafetyCatalog catalog = CatalogLoader.load(location, mode);
        releases.recordClasspath(catalog, location);
        return catalog;
    }

    /** 规则引擎。无状态，可以单例。 */
    @Bean
    public RuleSafetyEngine ruleSafetyEngine() {
        return new RuleSafetyEngine();
    }

    /** 模型没开就用空实现。默认关闭。 */
    @Bean
    public SignalExtractor signalExtractor(SafetyProperties properties, SafetyCatalog catalog) {
        if (!properties.getModel().isEnabled()) {
            return new DisabledSignalExtractor();
        }
        return new DeepSeekSignalExtractor(properties, catalog);
    }

    /** 安全状态计数。不含病情原文。 */
    @Bean
    public SafetyMetrics safetyMetrics() {
        return new SafetyMetrics();
    }

    /** 安全闸门。规则失败则安全降级。 */
    @Bean
    public SafetyGate safetyGate(SafetyCatalog catalog, RuleSafetyEngine engine, SignalExtractor extractor) {
        return new SafetyGate(catalog, engine, extractor);
    }
}
