package com.moonligh.consult.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Servlet 过滤器。JWT 本体在 {@link AuthTokensAutoConfiguration}，WebFlux 网关不走这里。
 */
@AutoConfiguration(after = AuthTokensAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AuthWebAutoConfiguration {

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
