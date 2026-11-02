package com.moonligh.consult.agent.domain.triage;

import com.moonligh.consult.agent.api.enums.ConsultationStatus;
import com.moonligh.consult.agent.api.enums.MissingSlot;
import com.moonligh.consult.agent.api.res.ConsultationRes;

/**
 * One consult round for Langfuse. Keys and generated labels are English;
 * user speech, user_prompt and catalog specialty stay as original text.
 */
public record TurnSnapshot(
        String node,
        String reason,
        String input,
        String prompt,
        String missingSlot,
        boolean cardReady,
        String specialty,
        String departmentSource) {

    private static final int TRACE_NAME_MAX = 48;
    private static final int POINT_MAX = 12;

    public static TurnSnapshot from(String node, String reason, String input, ConsultationRes view) {
        MissingSlot missing = view == null ? null : view.missingSlot();
        boolean ready = view != null && view.status() == ConsultationStatus.CARD_READY && view.card() != null;
        String spec = ready ? view.card().specialty() : null;
        String source = ready ? view.card().departmentSource() : null;
        String nextPrompt = view == null ? null : view.prompt();
        return new TurnSnapshot(node, reason, input, nextPrompt, missing == null ? null : missing.name(), ready, spec, source);
    }

    public static TurnSnapshot reject(String input, String code, String message) {
        return new TurnSnapshot("REJECT", code, input, message, null, false, null, null);
    }

    public String output() {
        if (isSafety()) {
            return "Safety stop. " + reasonLabel() + ". prompt: " + text(prompt);
        }
        if (cardReady) {
            String dept = text(specialty).isBlank() ? "unset" : specialty;
            String model = "MODEL".equals(departmentSource) ? "complaint model called." : "complaint model skipped.";
            return "Card \"" + dept + "\" · " + sourceLabel() + ". " + model;
        }
        return "No card. " + nodeLabel() + ". " + reasonLabel() + ". prompt: " + text(prompt);
    }

    public String traceName() {
        String result;
        String nodeLabel;
        String point;
        if (isSafety()) {
            result = "stop";
            nodeLabel = "safety";
            point = clip(input, POINT_MAX);
        } else if (cardReady) {
            result = "card";
            nodeLabel = text(specialty).isBlank() ? "unset" : specialty;
            point = sourceLabel();
        } else {
            result = "no_card";
            nodeLabel = nodeLabel();
            point = incompletePoint();
        }
        return clip(result + " · " + nodeLabel + " · " + point, TRACE_NAME_MAX);
    }

    public String nodeLabel() {
        if (node == null) {
            return "";
        }
        return switch (node) {
            case "N2_VAGUE" -> "vague_ask";
            case "N3_SLOT" -> "ask_patient";
            case "BIND_PATIENT" -> "bind_patient";
            case "N6_DEPARTMENT" -> "department";
            case "PATCH_CARD" -> "patch_card";
            case "SAFETY_STOP" -> "safety_stop";
            case "BLANK" -> "blank";
            case "DUPLICATE" -> "duplicate";
            case "REJECT" -> "reject";
            default -> node;
        };
    }

    public String reasonLabel() {
        if (reason == null || reason.isBlank()) {
            return "UNSET";
        }
        return reason;
    }

    boolean isSafety() {
        return "SAFETY_STOP".equals(node);
    }

    String sourceLabel() {
        return departmentSource == null || departmentSource.isBlank() ? "UNSET" : departmentSource;
    }

    private String incompletePoint() {
        if ("DURATION".equals(missingSlot)) {
            return "missing_duration";
        }
        if ("GENDER".equals(missingSlot) || "AGE".equals(missingSlot)) {
            String spoken = clip(input, POINT_MAX);
            if (!spoken.isBlank() && !looksLikeId(spoken)) {
                return spoken;
            }
            return "missing_patient";
        }
        String spoken = clip(input, POINT_MAX);
        return spoken.isBlank() ? nodeLabel() : spoken;
    }

    private static boolean looksLikeId(String value) {
        return value.length() >= 8
                && value.chars().noneMatch(ch -> Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN);
    }

    static String clip(String value, int max) {
        String text = text(value).replace('\n', ' ').trim();
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max);
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
