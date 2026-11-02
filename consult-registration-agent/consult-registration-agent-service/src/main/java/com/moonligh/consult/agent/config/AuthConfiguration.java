package com.moonligh.consult.agent.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.moonligh.consult.agent.infrastructure.http.ResilienceInterceptor;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;

/**
 * 身份服务客户端。JWT 过滤器在框架里。
 */
@Configuration
@EnableConfigurationProperties(IdentityProperties.class)
public class AuthConfiguration {

    @Bean
    public RestClient identityRestClient(
            IdentityProperties properties, CircuitBreaker identityCircuitBreaker, Bulkhead identityBulkhead) {
        // 派单、释放、延时都在会话锁外调；JDK HttpClient 复用长连接，虚拟线程下阻塞也不占平台线程
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .requestInterceptor(new ResilienceInterceptor(identityCircuitBreaker, identityBulkhead))
                .build();
    }
}
