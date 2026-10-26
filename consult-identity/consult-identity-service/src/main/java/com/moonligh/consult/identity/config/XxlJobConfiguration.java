package com.moonligh.consult.identity.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;

/** consult.xxl.enabled=true 时，向调度中心报名。 */
@Configuration
@EnableConfigurationProperties(XxlJobProperties.class)
public class XxlJobConfiguration {

    private static final Logger log = LoggerFactory.getLogger(XxlJobConfiguration.class);

    @Bean
    @ConditionalOnProperty(prefix = "consult.xxl", name = "enabled", havingValue = "true")
    public XxlJobSpringExecutor xxlJobExecutor(XxlJobProperties properties) {
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(properties.getAdminAddresses());
        executor.setAppname(properties.getAppname());
        executor.setPort(properties.getPort());
        executor.setLogPath(properties.getLogPath());
        executor.setLogRetentionDays(properties.getLogRetentionDays());
        if (notBlank(properties.getIp())) {
            executor.setIp(properties.getIp());
        }
        if (notBlank(properties.getAccessToken())) {
            executor.setAccessToken(properties.getAccessToken());
        }
        log.info("XXL-Job 执行器 appname={} port={} admin={}",
                properties.getAppname(), properties.getPort(), properties.getAdminAddresses());
        return executor;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
