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
        String payment = properties.getPaymentUri();
        String benefit = properties.getBenefitUri();
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
                .route("payment-notify", r -> r.path("/api/pay/notify/**").uri(payment))
                .route("payment-api", r -> r.path("/api/payments", "/api/payments/**").uri(payment))
                .route("benefit-api", r -> r.path("/api/benefit", "/api/benefit/**").uri(benefit))
                .route("identity-doctor-inbox", r -> r.path("/api/doctor/inbox", "/api/doctor/inbox/**").uri(identity))
                .route("identity-doctor-heartbeat", r -> r.path("/api/doctor/heartbeat").uri(identity))
                .route("agent-doctor", r -> r.path("/api/doctor", "/api/doctor/**").uri(agent))
                .route("agent-pay-callback", r -> r.path("/api/pay/callback").uri(agent))
                .route("agent-consult", r -> r.path("/api/consultations", "/api/consultations/**").uri(agent))
                .route("agent-departments", r -> r.path("/api/departments", "/api/departments/**").uri(agent))
                .build();
    }
}
