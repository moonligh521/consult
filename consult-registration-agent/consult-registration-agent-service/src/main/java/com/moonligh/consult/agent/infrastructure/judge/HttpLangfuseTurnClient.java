package com.moonligh.consult.agent.infrastructure.judge;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moonligh.consult.agent.domain.judge.TurnJudge;
import com.moonligh.consult.framework.observability.ObservabilityProperties;

public class HttpLangfuseTurnClient implements LangfuseTurnPort {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC);

    private final ObservabilityProperties properties;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public HttpLangfuseTurnClient(ObservabilityProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<TurnJudge.Observation> fetchTurns(int sinceMinutes, int limit) {
        String host = host();
        Instant start = Instant.now().minusSeconds(Math.max(1, sinceMinutes) * 60L);
        int pageSize = Math.min(100, Math.max(1, limit));
        List<TurnJudge.Observation> items = new ArrayList<>();
        int page = 1;
        while (items.size() < limit) {
            String url = host + "/api/public/observations?name=" + TurnJudge.OBSERVATION_NAME
                    + "&fromStartTime=" + URLEncoder.encode(ISO.format(start), StandardCharsets.UTF_8)
                    + "&limit=" + pageSize
                    + "&page=" + page;
            JsonNode payload = request("GET", url, null);
            JsonNode batch = payload.path("data");
            if (!batch.isArray() || batch.isEmpty()) {
                break;
            }
            int before = items.size();
            for (JsonNode node : batch) {
                TurnJudge.Observation obs = readObservation(node);
                if (obs != null) {
                    items.add(obs);
                }
                if (items.size() >= limit) {
                    break;
                }
            }
            if (batch.size() < pageSize || items.size() == before) {
                break;
            }
            page++;
        }
        return items.size() > limit ? items.subList(0, limit) : items;
    }

    @Override
    public void postScore(TurnJudge.Score score) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("id", score.scoreId());
        body.put("traceId", score.traceId());
        body.put("observationId", score.observationId());
        body.put("name", score.name());
        body.put("value", score.value());
        body.put("comment", score.comment());
        body.put("dataType", "NUMERIC");
        body.put("source", "API");
        request("POST", host() + "/api/public/scores", body);
    }

    private JsonNode request(String method, String url, JsonNode body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", auth())
                    .header("Accept", "application/json");
            if (body != null) {
                builder.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)));
            } else {
                builder.GET();
            }
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("langfuse HTTP " + response.statusCode());
            }
            String raw = response.body();
            if (raw == null || raw.isBlank()) {
                return MAPPER.createObjectNode();
            }
            return MAPPER.readTree(raw);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("langfuse interrupted", error);
        } catch (IllegalStateException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("langfuse unavailable", error);
        }
    }

    private TurnJudge.Observation readObservation(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        return new TurnJudge.Observation(
                node.path("id").asText(""),
                node.path("traceId").asText(""),
                node.path("name").asText(""),
                asText(node.get("input")),
                asText(node.get("output")),
                metadata(node.get("metadata")));
    }

    private static Map<String, Object> metadata(JsonNode node) {
        Map<String, Object> meta = new LinkedHashMap<>();
        if (node == null || node.isNull()) {
            return meta;
        }
        if (node.isTextual()) {
            try {
                node = MAPPER.readTree(node.asText());
            } catch (Exception error) {
                return meta;
            }
        }
        if (!node.isObject()) {
            return meta;
        }
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String key = names.next();
            JsonNode value = node.get(key);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isBoolean()) {
                meta.put(key, value.booleanValue());
            } else if (value.isNumber()) {
                meta.put(key, value.numberValue());
            } else {
                meta.put(key, value.asText());
            }
        }
        return meta;
    }

    private static String asText(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText();
        }
        return node.toString();
    }

    private String host() {
        String url = properties.getLangfuse().getPublicUrl();
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("langfuse url missing");
        }
        return url.replaceAll("/+$", "");
    }

    private String auth() {
        String pair = properties.getLangfuse().getPublicKey() + ":" + properties.getLangfuse().getSecretKey();
        return "Basic " + Base64.getEncoder().encodeToString(pair.getBytes(StandardCharsets.UTF_8));
    }
}
