package com.moonligh.benefit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import com.moonligh.consult.security.SharedSecretHeaderFilter;

/**
 * 发卡、查账和预占都认 X-Benefit-Token。
 */
@Configuration
@EnableConfigurationProperties(BenefitProperties.class)
public class BenefitSecurityConfiguration {

    @Bean
    public FilterRegistrationBean<SharedSecretHeaderFilter> benefitTokenFilter(BenefitProperties properties) {
        FilterRegistrationBean<SharedSecretHeaderFilter> bean = new FilterRegistrationBean<>(
                new SharedSecretHeaderFilter(
                        "X-Benefit-Token",
                        properties.getToken(),
                        "/api/benefit",
                        SharedSecretHeaderFilter.FailBody.SIMPLE_UNAUTHORIZED));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        bean.addUrlPatterns("/api/benefit", "/api/benefit/*");
        return bean;
    }
}
