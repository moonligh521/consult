package com.moonligh.consult.agent.config;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonligh.consult.agent.domain.DepartmentRouter;
import com.moonligh.consult.agent.domain.RegistrationAgent;
import com.moonligh.consult.agent.domain.department.DepartmentCatalog;
import com.moonligh.consult.agent.domain.department.DepartmentCatalogWriter;
import com.moonligh.consult.agent.domain.department.LexiconWriteRules;
import com.moonligh.consult.agent.domain.department.ScheduledDelayScheduler;
import com.moonligh.consult.agent.domain.safety.SafetyCatalog;
import com.moonligh.consult.agent.domain.triage.ComplaintModel;
import com.moonligh.consult.agent.domain.triage.TriageEngine;
import com.moonligh.consult.agent.infrastructure.department.CachedDepartmentCatalog;
import com.moonligh.consult.agent.infrastructure.persistence.DepartmentCatalogMapper;
import com.moonligh.consult.agent.infrastructure.department.DepartmentCatalogStoreImpl;
import com.moonligh.consult.agent.infrastructure.department.RedisDepartmentCache;
import com.moonligh.consult.framework.observability.AgentTrace;

/**
 * 科室目录：MySQL 为源，Redis 做校验快照，变更走延时双删。
 */
@Configuration
@EnableConfigurationProperties({
        DepartmentProperties.class,
        InquiryProperties.class,
        SafetyProperties.class,
        CatalogProperties.class})
public class DepartmentConfiguration {

    @Bean
    public RedisDepartmentCache redisDepartmentCache(
            StringRedisTemplate redis,
            ObjectMapper mapper,
            DepartmentProperties properties) {
        return new RedisDepartmentCache(
                redis,
                mapper,
                properties.getRedisKey(),
                Duration.ofSeconds(properties.getTtlSeconds()),
                properties.getLocalTtlMs());
    }

    @Bean
    public DepartmentCatalogStoreImpl departmentCatalogStore(DepartmentCatalogMapper catalog) {
        return new DepartmentCatalogStoreImpl(catalog);
    }

    @Bean
    public DepartmentCatalog departmentCatalog(
            RedisDepartmentCache cache,
            DepartmentCatalogStoreImpl store) {
        return new CachedDepartmentCatalog(cache, store);
    }

    @Bean
    public ScheduledDelayScheduler departmentDelayScheduler() {
        return new ScheduledDelayScheduler();
    }

    @Bean
    public LexiconWriteRules lexiconWriteRules(SafetyCatalog catalog) {
        return new LexiconWriteRules(catalog);
    }

    @Bean
    public DepartmentCatalogWriter departmentCatalogWriter(
            RedisDepartmentCache cache,
            DepartmentCatalogStoreImpl store,
            ScheduledDelayScheduler scheduler,
            DepartmentProperties properties,
            LexiconWriteRules lexiconRules) {
        return new DepartmentCatalogWriter(
                cache, store, scheduler, properties.getDoubleDeleteDelayMs(), lexiconRules);
    }

    @Bean
    public DepartmentRouter departmentRouter(DepartmentCatalog catalog) {
        return new DepartmentRouter(catalog);
    }

    @Bean
    public TriageEngine triageEngine(
            DepartmentCatalog catalog,
            DepartmentRouter router,
            ComplaintModel complaintModel,
            AgentTrace trace) {
        return new TriageEngine(catalog, router, complaintModel, trace);
    }

    @Bean
    public RegistrationAgent registrationAgent(TriageEngine engine, DepartmentCatalog catalog) {
        return new RegistrationAgent(engine, catalog);
    }
}
