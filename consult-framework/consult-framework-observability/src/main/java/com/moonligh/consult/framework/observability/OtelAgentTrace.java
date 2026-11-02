package com.moonligh.consult.framework.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

/**
 * 用 OTel API 写 span。属性写入失败只打日志，不影响业务。
 */
public final class OtelAgentTrace implements AgentTrace {

    private static final Logger log = LoggerFactory.getLogger(OtelAgentTrace.class);
    private static final String DRAFT_CONSULTATION_ID = "draft";

    private final ThreadLocal<AgentTraceContext> bound = new ThreadLocal<>();
    private final Tracer tracer;
    private final ObservabilityProperties properties;

    public OtelAgentTrace(OpenTelemetry openTelemetry, ObservabilityProperties properties) {
        this.tracer = openTelemetry.getTracer("consult-agent");
        this.properties = properties;
    }

    @Override
    public AgentSpan span(String name) {
        return start(name, false);
    }

    @Override
    public AgentGeneration generation(String name) {
        return start(name, true);
    }

    @Override
    public void bind(AgentTraceContext incoming) {
        if (incoming == null) {
            return;
        }
        AgentTraceContext current = bound.get();
        if (current == null) {
            bound.set(incoming);
        } else {
            boolean sameConsultation = incoming.consultationId() == null
                    || incoming.consultationId().isBlank()
                    || incoming.consultationId().equals(current.consultationId());
            bound.set(new AgentTraceContext(
                    first(incoming.consultationId(), current.consultationId()),
                    first(incoming.runId(), current.runId()),
                    incoming.messageSeq() != null ? incoming.messageSeq() : current.messageSeq(),
                    first(incoming.decisionId(), current.decisionId()),
                    first(incoming.catalogVersion(), current.catalogVersion()),
                    first(incoming.promptVersion(), current.promptVersion()),
                    sameConsultation ? first(incoming.userId(), current.userId()) : first(incoming.userId(), null)));
        }
        stampCurrent();
    }

    private static String first(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
    }

    @Override
    public void clear() {
        bound.remove();
    }

    @Override
    public String currentTraceId() {
        SpanContext context = Span.current().getSpanContext();
        return context.isValid() ? context.getTraceId() : null;
    }

    private OtelSpan start(String name, boolean generation) {
        try {
            Span parent = Span.current();
            Span span = tracer.spanBuilder(name == null || name.isBlank() ? "agent.span" : name).startSpan();
            Scope scope = span.makeCurrent();
            OtelSpan wrapper = new OtelSpan(
                    span,
                    scope,
                    properties.isCapturePayloads(),
                    parent.getSpanContext().isValid() ? parent : null);
            if (generation) {
                wrapper.attr("langfuse.observation.type", "generation");
            }
            applyContext(wrapper);
            return wrapper;
        } catch (RuntimeException error) {
            log.warn("start span failed type={}", error.getClass().getSimpleName());
            return new OtelSpan(null, null, false);
        }
    }

    private void stampCurrent() {
        Span current = Span.current();
        if (!current.getSpanContext().isValid()) {
            return;
        }
        applyContext(new OtelSpan(current, null, false));
    }

    private void applyContext(OtelSpan span) {
        AgentTraceContext held = bound.get();
        if (held == null) {
            return;
        }
        String sessionId = sessionId(held);
        span.attr("consultation_id", sessionId);
        span.attr("run_id", held.runId());
        span.attr("message_seq", held.messageSeq());
        span.attr("decision_id", held.decisionId());
        span.attr("catalog_version", held.catalogVersion());
        span.attr("prompt_version", held.promptVersion());
        span.attr("user_id", held.userId());
        if (sessionId != null) {
            span.attr("langfuse.session.id", sessionId);
        }
        if (held.userId() != null && !held.userId().isBlank()) {
            span.attr("langfuse.user.id", held.userId());
        }
    }

    static String sessionId(AgentTraceContext bound) {
        String id = bound == null ? null : bound.consultationId();
        if (id == null || id.isBlank() || DRAFT_CONSULTATION_ID.equals(id)) {
            return null;
        }
        return id;
    }

    static final class OtelSpan implements AgentGeneration {
        private final Span span;
        private final Scope scope;
        private final boolean capturePayloads;
        private final Span parent;

        OtelSpan(Span span, Scope scope, boolean capturePayloads) {
            this(span, scope, capturePayloads, null);
        }

        OtelSpan(Span span, Scope scope, boolean capturePayloads, Span parent) {
            this.span = span;
            this.scope = scope;
            this.capturePayloads = capturePayloads;
            this.parent = parent;
        }

        @Override
        public AgentGeneration attr(String key, Object value) {
            if (span == null || value == null || !SensitiveAttributeFilter.allow(key, value)) {
                return this;
            }
            if (!capturePayloads && payloadKey(key)) {
                try {
                    span.setAttribute("payload_captured", false);
                } catch (RuntimeException error) {
                    log.warn("set span attr failed key=payload_captured type={}", error.getClass().getSimpleName());
                }
                return this;
            }
            try {
                if (value instanceof Boolean flag) {
                    span.setAttribute(key, flag);
                } else if (value instanceof Integer number) {
                    span.setAttribute(key, number.longValue());
                } else if (value instanceof Long number) {
                    span.setAttribute(key, number);
                } else if (value instanceof Double number) {
                    span.setAttribute(key, number);
                } else {
                    span.setAttribute(AttributeKey.stringKey(key), String.valueOf(value));
                }
                if ("langfuse.trace.name".equals(key)
                        && parent != null
                        && parent.getSpanContext().isValid()) {
                    parent.setAttribute(AttributeKey.stringKey(key), String.valueOf(value));
                }
            } catch (RuntimeException error) {
                log.warn("set span attr failed key={} type={}", key, error.getClass().getSimpleName());
            }
            return this;
        }

        @Override
        public AgentSpan event(String name) {
            if (span != null && name != null && !name.isBlank()) {
                try {
                    span.addEvent(name);
                } catch (RuntimeException error) {
                    log.warn("add span event failed type={}", error.getClass().getSimpleName());
                }
            }
            return this;
        }

        @Override
        public void fail(String message) {
            if (span == null) {
                return;
            }
            try {
                span.setStatus(StatusCode.ERROR, message == null ? "" : message);
            } catch (RuntimeException error) {
                log.warn("mark span failed type={}", error.getClass().getSimpleName());
            }
        }

        @Override
        public void close() {
            try {
                if (scope != null) {
                    scope.close();
                }
            } catch (RuntimeException error) {
                log.warn("close span scope failed type={}", error.getClass().getSimpleName());
            }
            try {
                if (span != null) {
                    span.end();
                }
            } catch (RuntimeException error) {
                log.warn("end span failed type={}", error.getClass().getSimpleName());
            }
        }

        @Override
        public AgentGeneration model(String name) {
            return attr("gen_ai.request.model", name).attr("model", name);
        }

        @Override
        public AgentGeneration input(String prompt) {
            if (!capturePayloads) {
                return attr("payload_captured", false);
            }
            return attr("gen_ai.prompt", prompt).attr("input", prompt);
        }

        @Override
        public AgentGeneration output(String completion) {
            if (!capturePayloads) {
                return this;
            }
            return attr("gen_ai.completion", completion).attr("output", completion);
        }

        private static boolean payloadKey(String key) {
            return "langfuse.observation.input".equals(key)
                    || "langfuse.observation.output".equals(key)
                    || "complaint".equals(key)
                    || "user_prompt".equals(key);
        }

        @Override
        public AgentGeneration usage(int promptTokens, int completionTokens) {
            attr("gen_ai.usage.input_tokens", promptTokens);
            attr("gen_ai.usage.output_tokens", completionTokens);
            return this;
        }
    }
}
