package com.moonligh.consult.security;

import java.time.Duration;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * JWT 签发解析。Servlet 和 WebFlux 都装这一份，过滤器另走 {@link AuthWebAutoConfiguration}。
 */
@AutoConfiguration
@EnableConfigurationProperties(ConsultAuthProperties.class)
public class AuthTokensAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AuthTokens authTokens(ConsultAuthProperties properties) {
        return new AuthTokens(properties.getJwtSecret(), Duration.ofHours(properties.getJwtTtlHours()));
    }
}
