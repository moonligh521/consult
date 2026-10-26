package com.moonligh.consult.gateway;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 登录和就诊人进身份服务，咨询进挂号服务。
 */
@Configuration
public class GatewayRoutes {

    @Bean
    public RouteLocator consultRoutes(RouteLocatorBuilder builder, GatewayProperties properties) {
        String identity = properties.getIdentityUri();
        String agent = properties.getAgentUri();
        return builder.routes()
                .route("identity-auth", r -> r.path("/api/auth/**").uri(identity))
                .route("identity-patients", r -> r.path("/api/patients/**").uri(identity))
                .route("identity-admin-login", r -> r.path("/api/admin/login").uri(identity))
                .route("identity-admin", r -> r.path(
                                "/api/admin/accounts",
                                "/api/admin/accounts/**",
                                "/api/admin/patients",
                                "/api/admin/patients/**",
                                "/api/admin/me",
                                "/api/admin/doctors",
                                "/api/admin/doctors/**",
                                "/api/admin/schedules",
                                "/api/admin/schedules/**")
                        .uri(identity))
                .route("agent-admin", r -> r.path(
                                "/api/admin/consultations",
                                "/api/admin/consultations/**",
                                "/api/admin/departments",
                                "/api/admin/departments/**")
                        .uri(agent))
                .route("agent-consult", r -> r.path("/api/consultations/**").uri(agent))
                .build();
    }
}
