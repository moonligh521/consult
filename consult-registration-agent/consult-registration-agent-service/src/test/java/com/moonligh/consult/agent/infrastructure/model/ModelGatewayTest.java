package com.moonligh.consult.agent.infrastructure.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.moonligh.consult.agent.config.ModelProperties;
import com.moonligh.consult.agent.domain.safety.ModelStatus;
import com.sun.net.httpserver.HttpServer;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class ModelGatewayTest {

    private HttpServer server;
    private final AtomicInteger hits = new AtomicInteger();

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private ModelGateway gateway(int status, String content, long delayMs, int timeoutMs) throws IOException {
        return gateway(status, content, delayMs, timeoutMs, ModelQuota.NONE);
    }

    private ModelGateway gateway(int status, String content, long delayMs, int timeoutMs, ModelQuota quota)
            throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            hits.incrementAndGet();
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
            String body = "{\"choices\":[{\"message\":{\"content\":" + quote(content) + "}}]}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        ModelProperties props = new ModelProperties();
        props.setApiKey("test-key");
        props.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        props.getComplaint().setTimeoutMs(timeoutMs);
        props.getSafety().setTimeoutMs(timeoutMs);
        return new ModelGateway(props, null, new SimpleMeterRegistry(), quota);
    }

    @Test
    void globalQuotaExhaustedIsBusyWithoutRequest() throws IOException {
        ModelQuota exhausted = new ModelQuota() {
            @Override
            public Permit acquire(ModelPurpose purpose, long waitMs) {
                return null;
            }

            @Override
            public void release(Permit permit) {
            }
        };
        ModelReply reply = gateway(200, "{}", 0, 2000, exhausted).chat(ModelPurpose.SAFETY, "s", "u", null, 0);
        assertEquals(ModelStatus.BUSY, reply.status());
        assertEquals(0, hits.get());
    }

    @Test
    void globalPermitIsReleasedAfterCall() throws IOException {
        AtomicInteger held = new AtomicInteger();
        ModelQuota counting = new ModelQuota() {
            @Override
            public Permit acquire(ModelPurpose purpose, long waitMs) {
                held.incrementAndGet();
                return new Permit(purpose, "p");
            }

            @Override
            public void release(Permit permit) {
                held.decrementAndGet();
            }
        };
        ModelGateway gateway = gateway(500, "x", 0, 2000, counting);
        gateway.chat(ModelPurpose.COMPLAINT, "s", "u", null, 0);
        assertEquals(0, held.get());
    }

    private static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    @Test
    void successReturnsContent() throws IOException {
        ModelReply reply = gateway(200, "{\"terms\":[]}", 0, 2000).chat(ModelPurpose.COMPLAINT, "s", "u", null, 0);
        assertEquals(ModelStatus.SUCCESS, reply.status());
        assertEquals("{\"terms\":[]}", reply.content());
    }

    @Test
    void slowUpstreamTimesOut() throws IOException {
        ModelReply reply = gateway(200, "{}", 1500, 200).chat(ModelPurpose.COMPLAINT, "s", "u", null, 0);
        assertEquals(ModelStatus.TIMEOUT, reply.status());
    }

    @Test
    void missingKeyIsUnavailableWithoutRequest() {
        ModelProperties props = new ModelProperties();
        ModelGateway gateway = new ModelGateway(props, null, new SimpleMeterRegistry());
        assertFalse(gateway.hasKey());
        assertEquals(ModelStatus.UNAVAILABLE, gateway.chat(ModelPurpose.SAFETY, "s", "u", null, 0).status());
    }

    @Test
    void repeatedFailuresOpenBreakerAndStopSending() throws IOException {
        ModelGateway gateway = gateway(500, "x", 0, 2000);
        for (int i = 0; i < 10; i++) {
            assertEquals(ModelStatus.UNAVAILABLE, gateway.chat(ModelPurpose.COMPLAINT, "s", "u", null, 0).status());
        }
        int sent = hits.get();
        assertTrue(gateway.breakerOpen(ModelPurpose.COMPLAINT));
        assertEquals(ModelStatus.BREAKER_OPEN, gateway.chat(ModelPurpose.COMPLAINT, "s", "u", null, 0).status());
        assertEquals(sent, hits.get());
        assertFalse(gateway.breakerOpen(ModelPurpose.SAFETY));
    }

    @Test
    void breakerHalfOpenLetsOneProbe() {
        AtomicLong now = new AtomicLong(0);
        CircuitBreaker breaker = new CircuitBreaker(20, 10, 0.5, 30_000, now::get);
        for (int i = 0; i < 10; i++) {
            assertTrue(breaker.tryPass());
            breaker.record(false);
        }
        assertTrue(breaker.isOpen());
        assertFalse(breaker.tryPass());
        now.set(30_001);
        assertTrue(breaker.tryPass());
        assertFalse(breaker.tryPass());
        breaker.record(true);
        assertFalse(breaker.isOpen());
        assertTrue(breaker.tryPass());
    }

    @Test
    void failedProbeReopens() {
        AtomicLong now = new AtomicLong(0);
        CircuitBreaker breaker = new CircuitBreaker(20, 10, 0.5, 30_000, now::get);
        for (int i = 0; i < 10; i++) {
            breaker.tryPass();
            breaker.record(false);
        }
        now.set(30_001);
        assertTrue(breaker.tryPass());
        breaker.record(false);
        assertTrue(breaker.isOpen());
    }
}
