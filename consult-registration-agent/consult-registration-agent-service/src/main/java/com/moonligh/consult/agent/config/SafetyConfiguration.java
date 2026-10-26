package com.moonligh.consult.agent.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import com.moonligh.consult.agent.domain.safety.CatalogLoader;
import com.moonligh.consult.agent.domain.safety.DraftMode;
import com.moonligh.consult.agent.domain.safety.ModelTriggerScan;
import com.moonligh.consult.agent.domain.safety.RuleSafetyEngine;
import com.moonligh.consult.agent.domain.safety.SafetyCatalog;
import com.moonligh.consult.agent.domain.safety.SafetyGate;
import com.moonligh.consult.agent.domain.safety.SafetyMetrics;
import com.moonligh.consult.agent.domain.safety.SignalExtractor;
import com.moonligh.consult.agent.infrastructure.model.DeepSeekSignalExtractor;
import com.moonligh.consult.agent.infrastructure.model.ModelGateway;
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

    /** 模型触发词表。坏了起不来。 */
    @Bean
    public ModelTriggerScan modelTriggerScan() {
        return ModelTriggerScan.load();
    }

    /** 安全模型。没有开关：只在触发词命中时调用，失败走安全降级。 */
    @Bean
    public SignalExtractor signalExtractor(ModelGateway gateway, SafetyCatalog catalog) {
        return new DeepSeekSignalExtractor(gateway, catalog);
    }

    /** 安全状态计数。不含病情原文。 */
    @Bean
    public SafetyMetrics safetyMetrics() {
        return new SafetyMetrics();
    }

    /** 安全闸门。规则能定就不调模型；规则失败则安全降级。 */
    @Bean
    public SafetyGate safetyGate(
            SafetyCatalog catalog,
            RuleSafetyEngine engine,
            SignalExtractor extractor,
            ModelTriggerScan triggers) {
        return new SafetyGate(catalog, engine, extractor, triggers);
    }
}
