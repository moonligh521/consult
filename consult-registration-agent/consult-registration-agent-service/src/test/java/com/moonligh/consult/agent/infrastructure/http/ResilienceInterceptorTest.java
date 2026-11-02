package com.moonligh.consult.agent.infrastructure.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpServer;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

class ResilienceInterceptorTest {

    private HttpServer server;
    private final AtomicInteger hits = new AtomicInteger();

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void downstreamFailuresOpenBreakerAndStopCalling() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/x", exchange -> {
            hits.incrementAndGet();
            exchange.sendResponseHeaders(503, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                out.flush();
            }
        });
        server.start();
        CircuitBreaker breaker = CircuitBreaker.of("t", CircuitBreakerConfig.custom()
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .build());
        Bulkhead bulkhead = Bulkhead.of("t", BulkheadConfig.custom().maxConcurrentCalls(4).build());
        RestClient client = RestClient.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .requestInterceptor(new ResilienceInterceptor(breaker, bulkhead))
                .build();

        for (int i = 0; i < 4; i++) {
            assertThrows(HttpServerErrorException.class, () -> client.get().uri("/x").retrieve().toBodilessEntity());
        }
        assertEquals(CircuitBreaker.State.OPEN, breaker.getState());
        assertThrows(CallNotPermittedException.class, () -> client.get().uri("/x").retrieve().toBodilessEntity());
        assertEquals(4, hits.get());
        assertEquals(4, bulkhead.getMetrics().getAvailableConcurrentCalls());
    }
}
