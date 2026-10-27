package com.moonligh.consult.gateway;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.moonligh.consult.framework.ErrorCode;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import reactor.core.publisher.Mono;

/**
 * 过载保护：本实例同时在转发的请求超过上限就直接 503，排在所有过滤器最前面，被拒的请求不验令牌、不碰 Redis。
 * 限流按人挡刷子，这里按总量挡洪峰：活动、故障恢复后的集中重试会让在途请求远超后端能消化的量，
 * 与其全部排队拖成超时，不如快速拒掉一部分让其余的按时完成。
 */
@Component
public class InFlightLimitGatewayFilter implements GlobalFilter, Ordered {

    private final int max;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final MeterRegistry meters;

    public InFlightLimitGatewayFilter(GatewayProperties properties, MeterRegistry meters) {
        this.max = properties.getMaxInFlight();
        this.meters = meters;
        Gauge.builder("gateway_in_flight_requests", inFlight, AtomicInteger::get).register(meters);
    }

    @Override
    public int getOrder() {
        return -200;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (max <= 0) {
            return chain.filter(exchange);
        }
        if (inFlight.incrementAndGet() > max) {
            inFlight.decrementAndGet();
            meters.counter("gateway_shed_total").increment();
            return GatewayResponses.reject(exchange, HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.SERVICE_BUSY);
        }
        return chain.filter(exchange).doFinally(signal -> inFlight.decrementAndGet());
    }

    int inFlight() {
        return inFlight.get();
    }
}
