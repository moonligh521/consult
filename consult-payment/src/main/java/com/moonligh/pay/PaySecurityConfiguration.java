package com.moonligh.pay;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import com.moonligh.consult.security.SharedSecretHeaderFilter;

/**
 * 下单和查询认 X-Pay-Token。渠道回调不走这里。
 */
@Configuration
@EnableConfigurationProperties(PayProperties.class)
public class PaySecurityConfiguration {

    @Bean
    public FilterRegistrationBean<SharedSecretHeaderFilter> payTokenFilter(PayProperties properties) {
        FilterRegistrationBean<SharedSecretHeaderFilter> bean = new FilterRegistrationBean<>(
                new SharedSecretHeaderFilter(
                        "X-Pay-Token",
                        properties.getToken(),
                        "/api/payments",
                        SharedSecretHeaderFilter.FailBody.SIMPLE_UNAUTHORIZED));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        bean.addUrlPatterns("/api/payments", "/api/payments/*");
        return bean;
    }
}
