package com.moonligh.consult.framework.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;

class OtelAgentTraceTest {

    @Test
    void parentChildAndGenerationAttributes() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        OpenTelemetrySdk otel = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                        .build())
                .build();
        ObservabilityProperties properties = new ObservabilityProperties();
        properties.setEnabled(true);
        properties.setCapturePayloads(true);
        OtelAgentTrace trace = new OtelAgentTrace(otel, properties);
        trace.bind(new AgentTraceContext("c1", "r1", 2, "d1", "cat", "p1"));
        try (AgentSpan parent = trace.span(AgentTraceNames.TRIAGE)) {
            parent.attr("normalized_text", "咳嗽");
            try (AgentGeneration generation = trace.generation(AgentTraceNames.MODEL)) {
                generation.model("deepseek-v4-flash")
                        .input("sys\n\nuser")
                        .output("{\"specialty\":\"呼吸内科\"}")
                        .usage(10, 4)
                        .attr("status", "SUCCESS");
            }
        }
        otel.getSdkTracerProvider().forceFlush();
        assertEquals(2, exporter.getFinishedSpanItems().size());
        SpanData child = exporter.getFinishedSpanItems().get(0);
        SpanData parent = exporter.getFinishedSpanItems().get(1);
        assertEquals(parent.getSpanContext().getSpanId(), child.getParentSpanContext().getSpanId());
        assertEquals("c1", parent.getAttributes().get(AttributeKey.stringKey("consultation_id")));
        assertEquals("r1", parent.getAttributes().get(AttributeKey.stringKey("run_id")));
        assertNull(parent.getAttributes().get(AttributeKey.stringKey("langfuse.user.id")));
        assertEquals("generation", child.getAttributes().get(AttributeKey.stringKey("langfuse.observation.type")));
        assertEquals("deepseek-v4-flash", child.getAttributes().get(AttributeKey.stringKey("gen_ai.request.model")));
        assertEquals("sys\n\nuser", child.getAttributes().get(AttributeKey.stringKey("gen_ai.prompt")));
        otel.close();
    }

    @Test
    void dropsSensitiveAttributes() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        OpenTelemetrySdk otel = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                        .build())
                .build();
        ObservabilityProperties properties = new ObservabilityProperties();
        properties.setCapturePayloads(true);
        OtelAgentTrace trace = new OtelAgentTrace(otel, properties);
        try (AgentSpan span = trace.span("agent.safety")) {
            span.attr("patient_name", "张三")
                    .attr("Authorization", "Bearer abc")
                    .attr("api_key", "sk-secret")
                    .attr("mobile", "13800138000")
                    .attr("specialty", "呼吸内科")
                    .attr("leak", "Authorization: Bearer xyz");
        }
        otel.getSdkTracerProvider().forceFlush();
        SpanData data = exporter.getFinishedSpanItems().get(0);
        assertNull(data.getAttributes().get(AttributeKey.stringKey("patient_name")));
        assertNull(data.getAttributes().get(AttributeKey.stringKey("Authorization")));
        assertNull(data.getAttributes().get(AttributeKey.stringKey("api_key")));
        assertNull(data.getAttributes().get(AttributeKey.stringKey("mobile")));
        assertNull(data.getAttributes().get(AttributeKey.stringKey("leak")));
        assertEquals("呼吸内科", data.getAttributes().get(AttributeKey.stringKey("specialty")));
        otel.close();
    }

    @Test
    void writesLangfuseUserIdFromAccountId() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        OpenTelemetrySdk otel = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                        .build())
                .build();
        ObservabilityProperties properties = new ObservabilityProperties();
        properties.setCapturePayloads(true);
        OtelAgentTrace trace = new OtelAgentTrace(otel, properties);
        trace.bind(new AgentTraceContext("c1", "r1", 2, "d1", "cat", "p1", "acct-87c3"));
        try (AgentSpan span = trace.span(AgentTraceNames.TRIAGE)) {
            span.attr("specialty", "呼吸内科");
        }
        otel.getSdkTracerProvider().forceFlush();
        SpanData data = exporter.getFinishedSpanItems().get(0);
        assertEquals("acct-87c3", data.getAttributes().get(AttributeKey.stringKey("langfuse.user.id")));
        assertEquals("acct-87c3", data.getAttributes().get(AttributeKey.stringKey("user_id")));
        assertEquals("c1", data.getAttributes().get(AttributeKey.stringKey("langfuse.session.id")));
        otel.close();
    }

    @Test
    void laterBindWithoutUserIdKeepsAccountId() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        OpenTelemetrySdk otel = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                        .build())
                .build();
        OtelAgentTrace trace = new OtelAgentTrace(otel, new ObservabilityProperties());
        trace.bind(new AgentTraceContext("c1", null, null, null, null, null, "acct-87c3"));
        trace.bind(new AgentTraceContext(null, "run-2", null, null, "cat", null));
        try (AgentSpan span = trace.span(AgentTraceNames.TRIAGE)) {
        }
        otel.getSdkTracerProvider().forceFlush();
        SpanData data = exporter.getFinishedSpanItems().get(0);
        assertEquals("acct-87c3", data.getAttributes().get(AttributeKey.stringKey("langfuse.user.id")));
        assertEquals("run-2", data.getAttributes().get(AttributeKey.stringKey("run_id")));
        otel.close();
    }

    @Test
    void doesNotCarryUserIdAcrossConsultations() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        OpenTelemetrySdk otel = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                        .build())
                .build();
        OtelAgentTrace trace = new OtelAgentTrace(otel, new ObservabilityProperties());
        trace.bind(new AgentTraceContext("c1", null, null, null, null, null, "acct-87c3"));
        trace.bind(new AgentTraceContext("c2", "run-2", null, null, "cat", null, null));
        try (AgentSpan span = trace.span(AgentTraceNames.TRIAGE)) {
        }
        otel.getSdkTracerProvider().forceFlush();
        SpanData data = exporter.getFinishedSpanItems().get(0);
        assertEquals("c2", data.getAttributes().get(AttributeKey.stringKey("langfuse.session.id")));
        assertNull(data.getAttributes().get(AttributeKey.stringKey("langfuse.user.id")));
        otel.close();
    }

    @Test
    void doesNotWriteMobileAsUserId() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        OpenTelemetrySdk otel = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                        .build())
                .build();
        OtelAgentTrace trace = new OtelAgentTrace(otel, new ObservabilityProperties());
        trace.bind(new AgentTraceContext("c1", null, null, null, null, null, "13800138000"));
        try (AgentSpan span = trace.span(AgentTraceNames.TRIAGE)) {
        }
        otel.getSdkTracerProvider().forceFlush();
        SpanData data = exporter.getFinishedSpanItems().get(0);
        assertNull(data.getAttributes().get(AttributeKey.stringKey("langfuse.user.id")));
        assertNull(data.getAttributes().get(AttributeKey.stringKey("user_id")));
        otel.close();
    }

    @Test
    void bindWritesSessionOntoCurrentOtelSpan() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        OpenTelemetrySdk otel = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                        .build())
                .build();
        OtelAgentTrace trace = new OtelAgentTrace(otel, new ObservabilityProperties());
        Span http = otel.getTracer("http").spanBuilder("http post").startSpan();
        try (Scope ignored = http.makeCurrent()) {
            trace.bind(new AgentTraceContext("c1", "r1", 2, "d1", "cat", "p1", "acct-87c3"));
        } finally {
            http.end();
        }
        otel.getSdkTracerProvider().forceFlush();
        SpanData data = exporter.getFinishedSpanItems().get(0);
        assertEquals("http post", data.getName());
        assertEquals("c1", data.getAttributes().get(AttributeKey.stringKey("langfuse.session.id")));
        assertEquals("c1", data.getAttributes().get(AttributeKey.stringKey("consultation_id")));
        assertEquals("acct-87c3", data.getAttributes().get(AttributeKey.stringKey("langfuse.user.id")));
        otel.close();
    }

    @Test
    void draftConsultationIdIsNotLangfuseSession() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        OpenTelemetrySdk otel = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                        .build())
                .build();
        OtelAgentTrace trace = new OtelAgentTrace(otel, new ObservabilityProperties());
        trace.bind(new AgentTraceContext("draft", "r1", 1, null, "cat", null, "acct-87c3"));
        try (AgentSpan span = trace.span(AgentTraceNames.TRIAGE)) {
        }
        otel.getSdkTracerProvider().forceFlush();
        SpanData data = exporter.getFinishedSpanItems().get(0);
        assertNull(data.getAttributes().get(AttributeKey.stringKey("langfuse.session.id")));
        assertEquals("acct-87c3", data.getAttributes().get(AttributeKey.stringKey("langfuse.user.id")));
        otel.close();
    }

    @Test
    void capturePayloadsFalseOmitsTurnInputOutput() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        OpenTelemetrySdk otel = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                        .build())
                .build();
        ObservabilityProperties properties = new ObservabilityProperties();
        properties.setCapturePayloads(false);
        OtelAgentTrace trace = new OtelAgentTrace(otel, properties);
        try (AgentSpan turn = trace.span(AgentTraceNames.TURN)) {
            turn.attr("turn_node", "N2_VAGUE");
            turn.attr("langfuse.observation.input", "有点不舒服");
            turn.attr("langfuse.observation.output", "No card. vague_ask. VAGUE_ASK.");
            turn.attr("user_prompt", "请您描述病情或者需要解决的健康相关问题");
        }
        otel.getSdkTracerProvider().forceFlush();
        SpanData data = exporter.getFinishedSpanItems().get(0);
        assertEquals("N2_VAGUE", data.getAttributes().get(AttributeKey.stringKey("turn_node")));
        assertEquals(Boolean.FALSE, data.getAttributes().get(AttributeKey.booleanKey("payload_captured")));
        assertNull(data.getAttributes().get(AttributeKey.stringKey("langfuse.observation.input")));
        assertNull(data.getAttributes().get(AttributeKey.stringKey("langfuse.observation.output")));
        assertNull(data.getAttributes().get(AttributeKey.stringKey("user_prompt")));
        otel.close();
    }

    @Test
    void turnNameIsCopiedOntoHttpRoot() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        OpenTelemetrySdk otel = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                        .build())
                .build();
        OtelAgentTrace trace = new OtelAgentTrace(otel, new ObservabilityProperties());
        Span http = otel.getTracer("http").spanBuilder("POST /consultations/messages").startSpan();
        try (Scope ignored = http.makeCurrent()) {
            try (AgentSpan turn = trace.span(AgentTraceNames.TURN)) {
                turn.attr("langfuse.trace.name", "no_card · vague_ask · 有点不舒服");
                turn.attr("langfuse.observation.input", "有点不舒服");
                turn.attr("langfuse.observation.output", "No card. vague_ask. VAGUE_ASK.");
            }
        } finally {
            http.end();
        }
        otel.getSdkTracerProvider().forceFlush();
        SpanData root = exporter.getFinishedSpanItems().stream()
                .filter(item -> "POST /consultations/messages".equals(item.getName()))
                .findFirst()
                .orElseThrow();
        SpanData turn = exporter.getFinishedSpanItems().stream()
                .filter(item -> AgentTraceNames.TURN.equals(item.getName()))
                .findFirst()
                .orElseThrow();
        assertEquals(
                "no_card · vague_ask · 有点不舒服",
                root.getAttributes().get(AttributeKey.stringKey("langfuse.trace.name")));
        assertEquals(
                "no_card · vague_ask · 有点不舒服",
                turn.getAttributes().get(AttributeKey.stringKey("langfuse.trace.name")));
        assertEquals("有点不舒服", turn.getAttributes().get(AttributeKey.stringKey("langfuse.observation.input")));
        otel.close();
    }

    @Test
    void noopNeverThrows() {
        AgentTrace trace = AgentTrace.NOOP;
        trace.bind(AgentTraceContext.consultation("c1"));
        try (AgentGeneration generation = trace.generation(AgentTraceNames.MODEL)) {
            generation.model("x").input("a").output("b").usage(1, 1).attr("k", "v").fail("x");
        }
        assertNull(trace.currentTraceId());
        trace.clear();
    }

    @Test
    void disabledUsesNoopBeanContract() {
        assertTrue(new ObservabilityProperties().isEnabled());
        assertTrue(new ObservabilityProperties().isCapturePayloads());
        assertTrue(AgentTrace.NOOP.span("x") instanceof AgentSpan);
    }
}
