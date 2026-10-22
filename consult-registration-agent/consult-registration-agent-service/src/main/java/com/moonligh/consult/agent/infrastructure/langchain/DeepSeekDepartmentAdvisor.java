package com.moonligh.consult.agent.infrastructure.langchain;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.StringJoiner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonligh.consult.agent.api.enums.Gender;
import com.moonligh.consult.agent.config.SafetyProperties;
import com.moonligh.consult.agent.domain.DepartmentAdvisor;
import com.moonligh.consult.agent.domain.DepartmentRouter;
import com.moonligh.consult.agent.domain.department.DepartmentCatalog;
import com.moonligh.consult.agent.domain.department.DepartmentRecord;
import com.moonligh.consult.agent.domain.department.DepartmentSnapshot;

/**
 * 关键词对不上时问 DeepSeek 建议科室。失败或越权则当作没建议。
 */
public class DeepSeekDepartmentAdvisor implements DepartmentAdvisor {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekDepartmentAdvisor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SafetyProperties.Model settings;
    private final DepartmentCatalog catalog;
    private final HttpClient http;

    public DeepSeekDepartmentAdvisor(SafetyProperties properties, DepartmentCatalog catalog) {
        this.settings = properties.getModel();
        this.catalog = catalog;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(settings.getTimeoutMs()))
                .build();
    }

    @Override
    public Optional<DepartmentRouter.Advice> suggest(String description, Integer age, Gender gender) {
        if (settings.getApiKey() == null || settings.getApiKey().isBlank()) {
            log.error("advisor unavailable reason=missing-key");
            return Optional.empty();
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(settings.getBaseUrl()) + "/chat/completions"))
                    .timeout(Duration.ofMillis(settings.getTimeoutMs()))
                    .header("Authorization", "Bearer " + settings.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody(description, age, gender)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.error("advisor unavailable status={}", response.statusCode());
                return Optional.empty();
            }
            return parse(description, response.body());
        } catch (Exception error) {
            log.error("advisor unavailable type={}", error.getClass().getName());
            return Optional.empty();
        }
    }

    private Optional<DepartmentRouter.Advice> parse(String original, String body) throws Exception {
        JsonNode root = MAPPER.readTree(body);
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end < start) {
            return Optional.empty();
        }
        JsonNode payload = MAPPER.readTree(content.substring(start, end + 1));
        String name = payload.path("department").asText("");
        DepartmentSnapshot snapshot = catalog.snapshot();
        DepartmentRouter.Advice named = enabledNamed(snapshot, name);
        if (named == null) {
            return Optional.empty();
        }
        String evidence = payload.path("evidence").asText("");
        if (!evidence.isBlank() && original != null && !original.contains(evidence)) {
            evidence = "";
        }
        return Optional.of(new DepartmentRouter.Advice(
                named.department(), named.specialty(), evidence.isBlank() ? null : evidence));
    }

    private String requestBody(String description, Integer age, Gender gender) throws Exception {
        StringJoiner names = new StringJoiner("、");
        for (DepartmentRecord dept : catalog.snapshot().enabled()) {
            if (dept.specialties().isEmpty()) {
                names.add(dept.name());
                continue;
            }
            for (var spec : dept.specialties()) {
                names.add(spec.name());
            }
        }
        String system = "只输出 JSON：{\"department\":\"全科\",\"evidence\":\"\"}。"
                + "department 只能是这些二级挂号科：" + names + "。"
                + "对不上词、体检、报告看不懂一律全科。"
                + "不得输出未开通科室。不得给诊疗建议。不得把急症改成普通挂号。"
                + "evidence 必须是原话子串，没有则空。不要输出 JSON 之外的文字。";
        var root = MAPPER.createObjectNode();
        root.put("model", settings.getModelName());
        var messages = MAPPER.createArrayNode();
        messages.add(MAPPER.createObjectNode().put("role", "system").put("content", system));
        messages.add(MAPPER.createObjectNode().put("role", "user").put("content",
                "age=" + age + " gender=" + gender + "\n" + description));
        root.set("messages", messages);
        return MAPPER.writeValueAsString(root);
    }

    private static DepartmentRouter.Advice enabledNamed(DepartmentSnapshot snapshot, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        DepartmentRecord parent = snapshot.parentOfSpecialty(name);
        if (parent != null) {
            return new DepartmentRouter.Advice(parent.toDepartment(), name, null);
        }
        for (DepartmentRecord dept : snapshot.enabled()) {
            if (name.equals(dept.name())) {
                return new DepartmentRouter.Advice(dept.toDepartment(), dept.defaultSpecialty(), null);
            }
        }
        return null;
    }

    private static String trimSlash(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}
