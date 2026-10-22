package com.moonligh.consult.agent.config;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonligh.consult.agent.domain.DepartmentAdvisor;
import com.moonligh.consult.agent.domain.DepartmentRouter;
import com.moonligh.consult.agent.domain.RegistrationAgent;
import com.moonligh.consult.agent.domain.department.DepartmentCatalog;
import com.moonligh.consult.agent.domain.department.DepartmentCatalogWriter;
import com.moonligh.consult.agent.domain.department.LexiconWriteRules;
import com.moonligh.consult.agent.domain.department.ScheduledDelayScheduler;
import com.moonligh.consult.agent.domain.safety.SafetyCatalog;
import com.moonligh.consult.agent.infrastructure.department.CachedDepartmentCatalog;
import com.moonligh.consult.agent.infrastructure.department.JdbcDepartmentCatalogStore;
import com.moonligh.consult.agent.infrastructure.department.RedisDepartmentCache;
import com.moonligh.consult.agent.infrastructure.langchain.DeepSeekDepartmentAdvisor;

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

    /** 默认关闭。打开后只在快路径没有关键词时建议科室。 */
    @Bean
    public DepartmentAdvisor departmentAdvisor(
            InquiryProperties inquiry,
            SafetyProperties safety,
            DepartmentCatalog catalog) {
        if (!inquiry.isAdvisorEnabled()) {
            return DepartmentAdvisor.disabled();
        }
        return new DeepSeekDepartmentAdvisor(safety, catalog);
    }

    @Bean
    public RegistrationAgent registrationAgent(
            DepartmentRouter router,
            DepartmentAdvisor advisor,
            DepartmentCatalog catalog) {
        return new RegistrationAgent(router, advisor, catalog);
    }
}
