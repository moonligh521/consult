package com.moonligh.consult.agent.config;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

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
import com.moonligh.consult.agent.infrastructure.department.JdbcDepartmentCatalogStore;
import com.moonligh.consult.agent.infrastructure.department.RedisDepartmentCache;

/**
 * 科室目录：MySQL 为源，Redis 做校验快照，变更走延时双删。
 */
@Configuration
@EnableConfigurationProperties({DepartmentProperties.class, InquiryProperties.class, SafetyProperties.class})
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
    public JdbcDepartmentCatalogStore jdbcDepartmentCatalogStore(JdbcTemplate jdbc) {
        return new JdbcDepartmentCatalogStore(jdbc);
    }

    @Bean
    public DepartmentCatalog departmentCatalog(
            RedisDepartmentCache cache,
            JdbcDepartmentCatalogStore store) {
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
            JdbcDepartmentCatalogStore store,
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
            ComplaintModel complaintModel) {
        return new TriageEngine(catalog, router, complaintModel);
    }

    @Bean
    public RegistrationAgent registrationAgent(TriageEngine engine, DepartmentCatalog catalog) {
        return new RegistrationAgent(engine, catalog);
    }
}
