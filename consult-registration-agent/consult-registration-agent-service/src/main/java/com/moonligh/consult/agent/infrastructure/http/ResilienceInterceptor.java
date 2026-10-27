package com.moonligh.consult.agent.infrastructure.http;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;

/**
 * 给下游 HTTP 调用套舱壁和熔断。舱壁限住同时打下游的请求数，下游变慢时本服务的请求不会全堆在这一个依赖上；
 * 熔断在下游持续失败或变慢时快速失败，让派单、查就诊人立刻走各自的兜底，而不是每个都等满超时。
 * 拒绝时抛的 BulkheadFullException / CallNotPermittedException 都是 RuntimeException，调用方按下游失败处理。
 */
public class ResilienceInterceptor implements ClientHttpRequestInterceptor {

    private final CircuitBreaker breaker;
    private final Bulkhead bulkhead;

    public ResilienceInterceptor(CircuitBreaker breaker, Bulkhead bulkhead) {
        this.breaker = breaker;
        this.bulkhead = bulkhead;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        bulkhead.acquirePermission();
        try {
            breaker.acquirePermission();
            long started = System.nanoTime();
            try {
                ClientHttpResponse response = execution.execute(request, body);
                long elapsed = System.nanoTime() - started;
                if (response.getStatusCode().is5xxServerError()) {
                    breaker.onError(elapsed, TimeUnit.NANOSECONDS,
                            new IOException("downstream status " + response.getStatusCode().value()));
                } else {
                    breaker.onSuccess(elapsed, TimeUnit.NANOSECONDS);
                }
                return response;
            } catch (IOException | RuntimeException error) {
                breaker.onError(System.nanoTime() - started, TimeUnit.NANOSECONDS, error);
                throw error;
            }
        } finally {
            bulkhead.onComplete();
        }
    }
}
