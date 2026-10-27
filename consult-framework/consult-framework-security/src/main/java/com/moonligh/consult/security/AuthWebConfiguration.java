package com.moonligh.consult.security;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Servlet 服务共用：签发解析 JWT、二次校验账号、校验内部令牌。
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(ConsultAuthProperties.class)
public class AuthWebConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AuthTokens authTokens(ConsultAuthProperties properties) {
        return new AuthTokens(properties.getJwtSecret(), Duration.ofHours(properties.getJwtTtlHours()));
    }

    @Bean
    @ConditionalOnMissingBean(name = "accountAuthFilter")
    public FilterRegistrationBean<AccountAuthFilter> accountAuthFilter(AuthTokens tokens) {
        FilterRegistrationBean<AccountAuthFilter> bean = new FilterRegistrationBean<>(new AccountAuthFilter(tokens));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        bean.addUrlPatterns("/api/*");
        return bean;
    }

    @Bean
    @ConditionalOnMissingBean(name = "internalTokenFilter")
    public FilterRegistrationBean<InternalTokenFilter> internalTokenFilter(ConsultAuthProperties properties) {
        FilterRegistrationBean<InternalTokenFilter> bean =
                new FilterRegistrationBean<>(new InternalTokenFilter(properties.getInternalToken()));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 21);
        bean.addUrlPatterns("/internal/*");
        return bean;
    }
}
