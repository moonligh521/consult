package com.moonligh.consult.agent.domain.judge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 旁路话术评分。不判断科室对错，不进患者请求。
 */
public final class TurnJudge {

    public static final String OBSERVATION_NAME = "agent.turn";
    public static final String PROMPT_GROUNDED = "prompt_grounded";
    public static final String VAGUE_LOOP = "vague_loop";

    private static final Pattern JSON_BLOCK = Pattern.compile("\\{.*\\}", Pattern.DOTALL);
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*\\}\\}");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TurnJudge() {
    }

    public enum Phase {
        BOOTSTRAP(1.0d),
        RECHECK(1.0d),
        STEADY(0.002d);

        private final double sampleRate;

        Phase(double sampleRate) {
            this.sampleRate = sampleRate;
        }

        public double sampleRate() {
            return sampleRate;
        }

        public static Phase from(String value) {
            if (value == null || value.isBlank()) {
                return BOOTSTRAP;
            }
            return Phase.valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
    }

    public record Observation(
            String id,
            String traceId,
            String name,
            String input,
            String output,
            Map<String, Object> metadata) {
        public Observation {
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }

    public record Score(String observationId, String traceId, String name, double value, String comment) {
        public String scoreId() {
            return name + "-" + observationId;
        }
    }

    public static Phase resolvePhase(String value) {
        return Phase.from(value);
    }

    public static double resolveRate(Phase phase, Double override) {
        if (override != null) {
            return clamp01(override);
        }
        return phase.sampleRate();
    }

    public static Map<String, String> fields(Observation obs) {
        Map<String, Object> meta = obs.metadata();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("input", text(obs.input()));
        fields.put("output", text(obs.output()));
        fields.put("turn_node", text(meta.get("turn_node")));
        fields.put("user_prompt", text(meta.get("user_prompt")));
        fields.put("missing_slot", text(meta.get("missing_slot")));
        fields.put("card_ready", text(meta.get("card_ready")));
        fields.put("payload_captured", text(meta.get("payload_captured")));
        return fields;
    }

    public static String skipReason(Observation obs) {
        if (obs == null || !OBSERVATION_NAME.equals(obs.name())) {
            return "name";
        }
        Map<String, String> fields = fields(obs);
        if ("false".equalsIgnoreCase(fields.get("payload_captured"))) {
            return "payload";
        }
        String input = fields.get("input").trim();
        if (input.isEmpty()) {
            return "empty";
        }
        if ("[redacted]".equals(input)) {
            return "redacted";
        }
        if (fields.get("output").trim().isEmpty()) {
            return "empty_output";
        }
        return null;
    }

    public static boolean isVagueAsk(Observation obs) {
        Map<String, String> fields = fields(obs);
        return "N2_VAGUE".equals(fields.get("turn_node")) || fields.get("output").contains("vague_ask");
    }

    public static List<String> evaluatorNames(Observation obs) {
        List<String> names = new ArrayList<>();
        names.add(PROMPT_GROUNDED);
        if (isVagueAsk(obs)) {
            names.add(VAGUE_LOOP);
        }
        return names;
    }

    public static boolean sampleHit(String observationId, double rate) {
        if (rate <= 0) {
            return false;
        }
        if (rate >= 1) {
            return true;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(observationId.getBytes(StandardCharsets.UTF_8));
            long value = ((digest[0] & 0xffL) << 24)
                    | ((digest[1] & 0xffL) << 16)
                    | ((digest[2] & 0xffL) << 8)
                    | (digest[3] & 0xffL);
            return value / 4294967295d < rate;
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    public static String render(String template, Map<String, String> variables) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String value = variables.getOrDefault(matcher.group(1), "");
            matcher.appendReplacement(out, Matcher.quoteReplacement(value == null ? "" : value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    public static Score parseScore(String observationId, String traceId, String name, String reply) {
        if (reply == null || reply.isBlank()) {
            throw new IllegalArgumentException("empty judge reply");
        }
        Matcher matcher = JSON_BLOCK.matcher(reply.trim());
        if (!matcher.find()) {
            throw new IllegalArgumentException("judge reply has no json");
        }
        try {
            JsonNode node = MAPPER.readTree(matcher.group());
            if (node.get("score") == null || node.get("score").isNull()) {
                throw new IllegalArgumentException("judge json missing score");
            }
            double score = clamp01(node.get("score").asDouble());
            String reason = node.path("reason").asText("unspecified").trim();
            if (reason.isEmpty()) {
                reason = "unspecified";
            }
            return new Score(observationId, traceId, name, score, reason);
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalArgumentException("judge json invalid", error);
        }
    }

    public static List<Score> evaluate(
            Observation obs,
            Map<String, String> prompts,
            Function<String, String> model) {
        List<Score> scores = new ArrayList<>();
        Map<String, String> fields = fields(obs);
        for (String name : evaluatorNames(obs)) {
            String template = prompts.get(name);
            if (template == null) {
                throw new IllegalArgumentException("missing prompt " + name);
            }
            String reply = model.apply(render(template, fields));
            scores.add(parseScore(obs.id(), obs.traceId(), name, reply));
        }
        return scores;
    }

    private static double clamp01(double value) {
        if (value < 0) {
            return 0;
        }
        if (value > 1) {
            return 1;
        }
        return value;
    }

    private static String text(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Boolean flag) {
            return flag ? "true" : "false";
        }
        return String.valueOf(value);
    }
}
