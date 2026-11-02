package com.moonligh.consult.framework.observability;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.moonligh.consult.framework.TraceIdSource;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;

/**
 * 装配 AgentTrace。没开开关或没有 OTel 时给 no-op，业务不用判断。
 */
@AutoConfiguration
@ConditionalOnClass(OpenTelemetry.class)
@EnableConfigurationProperties(ObservabilityProperties.class)
public class ObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AgentTrace agentTrace(ObjectProvider<OpenTelemetry> openTelemetry, ObservabilityProperties properties) {
        if (!properties.isEnabled()) {
            return AgentTrace.NOOP;
        }
        OpenTelemetry telemetry = openTelemetry.getIfAvailable();
        if (telemetry == null) {
            return AgentTrace.NOOP;
        }
        return new OtelAgentTrace(telemetry, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public TraceIdSource otelTraceIdSource() {
        return () -> {
            SpanContext context = Span.current().getSpanContext();
            return context.isValid() ? context.getTraceId() : null;
        };
    }
}
