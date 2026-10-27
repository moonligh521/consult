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
        // 结束问诊会同步调 release；不设超时前端会一直停在「正在结束」
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .build();
    }
}
