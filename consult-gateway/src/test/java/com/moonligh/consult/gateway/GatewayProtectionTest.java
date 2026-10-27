package com.moonligh.consult.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.InetSocketAddress;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import com.moonligh.consult.security.AuthTokens;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

class GatewayProtectionTest {

    private static final GatewayFilterChain PASS = exchange -> Mono.empty();

    private static GatewayProperties properties() {
        GatewayProperties props = new GatewayProperties();
        GatewayProperties.Rule sms = new GatewayProperties.Rule();
        sms.setName("sms");
        sms.setPaths(List.of("/api/auth/sms"));
        sms.setKeyBy(GatewayProperties.KeyBy.IP);
        sms.setPerSecond(1);
        sms.setBurst(2);
        GatewayProperties.Rule write = new GatewayProperties.Rule();
        write.setName("consult-write");
        write.setPaths(List.of("/api/consultations/**"));
        write.setMethods(List.of("POST"));
        write.setPerSecond(1);
        write.setBurst(3);
        props.getRateLimit().setRules(List.of(sms, write));
        return props;
    }

    private static RateLimitGatewayFilter rateLimiter(GatewayProperties props) {
        var noRedis = new DefaultListableBeanFactory().getBeanProvider(RedisRateLimiter.class);
        return new RateLimitGatewayFilter(props, noRedis, new SimpleMeterRegistry());
    }

    private static MockServerWebExchange post(String path, String account) {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.post(path)
                .remoteAddress(new InetSocketAddress("10.0.0.8", 5000));
        if (account != null) {
            request.header(AuthTokens.ACCOUNT_HEADER, account);
        }
        return MockServerWebExchange.from(request);
    }

    @Test
    void firstMatchingRuleWinsAndMethodFilterApplies() {
        RateLimitGatewayFilter filter = rateLimiter(properties());
        assertEquals("sms", filter.match(post("/api/auth/sms", null)).name());
        assertEquals("consult-write", filter.match(post("/api/consultations/messages", "a1")).name());
        MockServerWebExchange get = MockServerWebExchange.from(MockServerHttpRequest.get("/api/consultations/x"));
        assertEquals("default", filter.match(get).name());
    }

    @Test
    void withoutRedisLocalBucketRejectsPerAccount() {
        RateLimitGatewayFilter filter = rateLimiter(properties());
        for (int i = 0; i < 3; i++) {
            MockServerWebExchange ok = post("/api/consultations/messages", "a1");
            filter.filter(ok, PASS).block();
            assertNull(ok.getResponse().getStatusCode());
        }
        MockServerWebExchange limited = post("/api/consultations/messages", "a1");
        filter.filter(limited, PASS).block();
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, limited.getResponse().getStatusCode());

        MockServerWebExchange other = post("/api/consultations/messages", "a2");
        filter.filter(other, PASS).block();
        assertNull(other.getResponse().getStatusCode());
    }

    @Test
    void ipRuleIgnoresAccountHeader() {
        RateLimitGatewayFilter filter = rateLimiter(properties());
        filter.filter(post("/api/auth/sms", "a1"), PASS).block();
        filter.filter(post("/api/auth/sms", "a2"), PASS).block();
        MockServerWebExchange third = post("/api/auth/sms", "a3");
        filter.filter(third, PASS).block();
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, third.getResponse().getStatusCode());
    }

    @Test
    void inFlightOverLimitIsShedAndSlotFreedOnCompletion() {
        GatewayProperties props = new GatewayProperties();
        props.setMaxInFlight(1);
        InFlightLimitGatewayFilter filter = new InFlightLimitGatewayFilter(props, new SimpleMeterRegistry());
        Sinks.Empty<Void> slow = Sinks.empty();
        filter.filter(post("/api/x", "a1"), exchange -> slow.asMono()).subscribe();
        assertEquals(1, filter.inFlight());

        MockServerWebExchange shed = post("/api/x", "a2");
        filter.filter(shed, PASS).block();
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, shed.getResponse().getStatusCode());

        slow.tryEmitEmpty();
        assertEquals(0, filter.inFlight());
        MockServerWebExchange next = post("/api/x", "a3");
        filter.filter(next, PASS).block();
        assertNull(next.getResponse().getStatusCode());
    }
}
