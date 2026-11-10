package com.moonligh.consult.gateway;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.consult.framework.Result;
import com.moonligh.consult.security.AuthPrincipal;
import com.moonligh.consult.security.AuthTokens;

import reactor.core.publisher.Mono;

/**
 * 验登录令牌，改写 X-Account-Id。匿名接口放行。
 */
@Component
public class TokenGatewayFilter implements GlobalFilter, Ordered {

    private static final Set<String> OPEN = Set.of(
            "/api/auth/sms",
            "/api/auth/login",
            "/api/admin/login",
            "/actuator/health");

    private final AuthTokens tokens;
    private final ObjectMapper mapper = new ObjectMapper();

    public TokenGatewayFilter(AuthTokens tokens) {
        this.tokens = tokens;
    }

    @Override
    public int getOrder() {
        return -100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (OPEN.contains(path)
                || path.startsWith("/api/pay/notify/")
                || path.startsWith("/api/pay/callback")
                || path.startsWith("/api/payments")
                || path.startsWith("/api/benefit")
                || "OPTIONS".equalsIgnoreCase(exchange.getRequest().getMethod().name())) {
            return chain.filter(stamp(exchange, null));
        }
        try {
            AuthPrincipal principal = tokens.parse(exchange.getRequest().getHeaders().getFirst(AuthTokens.HEADER));
            if (path.startsWith("/api/admin/") && !principal.staff()) {
                throw new BusinessException(ErrorCode.LOGIN_REQUIRED);
            }
            if (path.startsWith("/api/doctor/") && !principal.staff()) {
                throw new BusinessException(ErrorCode.LOGIN_REQUIRED);
            }
            if (!path.startsWith("/api/admin/") && !path.startsWith("/api/doctor/") && principal.staff()) {
                throw new BusinessException(ErrorCode.LOGIN_REQUIRED);
            }
            return chain.filter(stamp(exchange, principal));
        } catch (BusinessException error) {
            return writeFail(exchange, error.getErrorCode() == null ? ErrorCode.LOGIN_REQUIRED : error.getErrorCode());
        }
    }

    private ServerWebExchange stamp(ServerWebExchange exchange, AuthPrincipal principal) {
        String accountId = principal == null ? null : principal.accountId();
        String doctorId = principal == null ? null : principal.doctorId();
        ServerHttpRequest mutated = exchange.getRequest().mutate().headers(headers -> {
            headers.remove(AuthTokens.ACCOUNT_HEADER);
            headers.remove(AuthTokens.DOCTOR_HEADER);
            if (accountId != null && !accountId.isBlank()) {
                headers.set(AuthTokens.ACCOUNT_HEADER, accountId);
            }
            if (doctorId != null && !doctorId.isBlank()) {
                headers.set(AuthTokens.DOCTOR_HEADER, doctorId);
            }
        }).build();
        return exchange.mutate().request(mutated).build();
    }

    private Mono<Void> writeFail(ServerWebExchange exchange, ErrorCode code) {
        byte[] bytes;
        try {
            bytes = mapper.writeValueAsBytes(Result.fail(code));
        } catch (JsonProcessingException error) {
            bytes = "{\"code\":1201,\"message\":\"请先登录。\"}".getBytes(StandardCharsets.UTF_8);
        }
        exchange.getResponse().getHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
