package com.moonligh.consult.agent.infrastructure.judge;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moonligh.consult.framework.model.ModelProperties;

/**
 * 独立 HTTP 调评分模型。超时 15s，不写 AgentTrace。
 */
public class HttpJudgeModelClient implements JudgeModelPort {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ModelProperties model;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public HttpJudgeModelClient(ModelProperties model) {
        this.model = model;
    }

    @Override
    public String complete(String prompt) {
        if (model.getApiKey() == null || model.getApiKey().isBlank()) {
            throw new IllegalStateException("missing judge model key");
        }
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("model", model.getModelName());
            root.put("temperature", 0);
            root.set("response_format", MAPPER.createObjectNode().put("type", "json_object"));
            var messages = MAPPER.createArrayNode();
            messages.add(MAPPER.createObjectNode().put("role", "system").put("content", "Return JSON only."));
            messages.add(MAPPER.createObjectNode().put("role", "user").put("content", prompt));
            root.set("messages", messages);
            String base = model.getBaseUrl() == null ? "" : model.getBaseUrl().replaceAll("/+$", "");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(base + "/chat/completions"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + model.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(root)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("judge model HTTP " + response.statusCode());
            }
            JsonNode content = MAPPER.readTree(response.body()).path("choices").path(0).path("message").path("content");
            String text = content.asText("");
            if (text.isBlank()) {
                throw new IllegalStateException("judge model empty");
            }
            return text;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("judge model interrupted", error);
        } catch (IllegalStateException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("judge model unavailable", error);
        }
    }
}
