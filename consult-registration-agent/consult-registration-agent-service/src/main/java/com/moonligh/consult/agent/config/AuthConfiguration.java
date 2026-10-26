package com.moonligh.consult.agent.config;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.client.RestClient;

import com.moonligh.consult.security.AccountAuthFilter;
import com.moonligh.consult.security.AuthTokens;

/**
 * 登录令牌、账号过滤、身份服务客户端。
 */
@Configuration
@EnableConfigurationProperties({AuthProperties.class, IdentityProperties.class})
public class AuthConfiguration {

    @Bean
    public AuthTokens authTokens(AuthProperties properties) {
        return new AuthTokens(properties.getJwtSecret(), Duration.ofHours(properties.getJwtTtlHours()));
    }

    @Bean
    public FilterRegistrationBean<AccountAuthFilter> accountAuthFilter(AuthTokens tokens) {
        FilterRegistrationBean<AccountAuthFilter> bean = new FilterRegistrationBean<>(new AccountAuthFilter(tokens));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        bean.addUrlPatterns("/api/*");
        return bean;
    }

    @Bean
    public RestClient identityRestClient(IdentityProperties properties) {
        return RestClient.builder().baseUrl(properties.getBaseUrl()).build();
    }
}
