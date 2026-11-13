package com.moonligh.consult.agent.domain.triage;

import com.moonligh.consult.agent.domain.safety.SafetyAssessment;
import com.moonligh.consult.framework.model.ModelStatus;
import com.moonligh.consult.framework.observability.AgentSpan;
import com.moonligh.consult.framework.observability.SensitiveAttributeFilter;

/**
 * Langfuse labels: English keys and generated summaries. Input keeps user text;
 * user_prompt and specialty keep product copy.
 */
public final class AgentTraceZh {

    private AgentTraceZh() {
    }

    public static void triage(AgentSpan span, String haystack, TriageResult result) {
        if (span == null || result == null || result.route() == null) {
            return;
        }
        String specialty = result.route().advice() == null ? "" : nullToEmpty(result.route().advice().specialty());
        boolean fallback = result.route().source() == DecisionSource.FALLBACK;
        String summary = triageSummary(specialty, result.route().source(), result.modelStatus(), fallback);
        span.attr("specialty", specialty)
                .attr("complaint", haystack)
                .attr("canon_source", nameOf(result.canon() == null ? null : result.canon().source()))
                .attr("route_source", nameOf(result.route().source()))
                .attr("canon_reason", result.canon() == null ? "" : result.canon().reason())
                .attr("route_reason", result.route().reason())
                .attr("model_status", nameOf(result.modelStatus()))
                .attr("fallback", fallback)
                .attr("langfuse.observation.input", haystack)
                .attr("langfuse.observation.output", summary);
    }

    public static void route(AgentSpan span, RouteDecision rule, RouteDecision result) {
        if (span == null || result == null) {
            return;
        }
        String specialty = result.advice() == null ? "" : nullToEmpty(result.advice().specialty());
        String keyword = result.hit() == null ? "" : nullToEmpty(result.hit().keyword());
        String summary = keyword.isBlank()
                ? "Routed to \"" + specialty + "\". " + nullToEmpty(result.reason())
                : "Keyword \"" + keyword + "\" -> \"" + specialty + "\". " + nullToEmpty(result.reason());
        span.attr("specialty", specialty)
                .attr("keyword", keyword)
                .attr("route_reason", result.reason())
                .attr("rule_reason", rule == null ? "" : rule.reason())
                .attr("langfuse.observation.output", summary);
    }

    public static void turn(AgentSpan span, TurnSnapshot snap) {
        if (span == null || snap == null) {
            return;
        }
        String output = snap.output();
        span.attr("turn_node", snap.node())
                .attr("turn_reason", snap.reason())
                .attr("missing_slot", snap.missingSlot())
                .attr("card_ready", snap.cardReady())
                .attr("specialty", snap.specialty())
                .attr("user_prompt", snap.prompt())
                .attr("langfuse.trace.name", snap.traceName())
                .attr("langfuse.observation.input", redact(snap.input()))
                .attr("langfuse.observation.output", output);
    }

    public static void safety(AgentSpan span, SafetyAssessment result) {
        if (span == null || result == null) {
            return;
        }
        String summary;
        if (result.urgent()) {
            summary = "Urgent latched. Safety model not called.";
        } else if (result.modelStatus() == ModelStatus.SKIPPED || result.modelStatus() == ModelStatus.DISABLED) {
            summary = "Not urgent. Safety model " + nameOf(result.modelStatus()) + ".";
        } else if (result.modelStatus() != null && result.modelStatus().failed()) {
            summary = "Safety model failed (" + result.modelStatus().name() + "). SAFE_FALLBACK.";
        } else {
            summary = "Safety model called. urgent=" + result.urgent() + ".";
        }
        span.attr("urgent", result.urgent())
                .attr("model_status", nameOf(result.modelStatus()))
                .attr("rule_certain", result.ruleCertain())
                .attr("langfuse.observation.output", summary);
    }

    static String triageSummary(String specialty, DecisionSource source, ModelStatus status, boolean fallback) {
        String dept = specialty == null || specialty.isBlank() ? "unset" : specialty;
        if (fallback) {
            return "FALLBACK to \"" + dept + "\".";
        }
        if (status == ModelStatus.SKIPPED || status == ModelStatus.DISABLED) {
            return "RULE routed to \"" + dept + "\", model " + (status == null ? "SKIPPED" : status.name()) + ".";
        }
        if (source == DecisionSource.MODEL) {
            return "MODEL routed to \"" + dept + "\".";
        }
        if (source == DecisionSource.PATIENT) {
            return "PATIENT confirmed \"" + dept + "\".";
        }
        return "Routed to \"" + dept + "\".";
    }

    private static String nameOf(Enum<?> value) {
        return value == null ? "" : value.name();
    }

    private static String redact(String value) {
        if (value == null) {
            return null;
        }
        return SensitiveAttributeFilter.blockedValue(value) ? "[redacted]" : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
