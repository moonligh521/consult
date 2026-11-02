package com.moonligh.consult.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.moonligh.consult.framework.TraceIdFilter;
import com.moonligh.consult.framework.observability.AgentTrace;

import reactor.core.publisher.Mono;

/**
 * 网关根 trace：把 OTel trace id 写成 X-Trace-Id 并下传。W3C traceparent 由 OTel 仪器自动传播。
 */
@Component
public class GatewayTraceFilter implements GlobalFilter, Ordered {

    private final AgentTrace trace;

    public GatewayTraceFilter(AgentTrace trace) {
        this.trace = trace == null ? AgentTrace.NOOP : trace;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(TraceIdFilter.HEADER);
        String traceId = firstNonBlank(trace.currentTraceId(), incoming);
        if (traceId == null || traceId.isBlank()) {
            return chain.filter(exchange);
        }
        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(TraceIdFilter.HEADER, traceId)
                .build();
        exchange.getResponse().getHeaders().set(TraceIdFilter.HEADER, traceId);
        return chain.filter(exchange.mutate().request(request).build());
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }
}
