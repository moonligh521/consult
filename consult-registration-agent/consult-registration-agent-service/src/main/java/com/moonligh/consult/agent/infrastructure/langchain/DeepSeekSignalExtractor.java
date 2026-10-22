package com.moonligh.consult.agent.infrastructure.langchain;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonligh.consult.agent.config.SafetyProperties;
import com.moonligh.consult.agent.domain.safety.ConsultationMessage;
import com.moonligh.consult.agent.domain.safety.Demographics;
import com.moonligh.consult.agent.domain.safety.ExtractedSignal;
import com.moonligh.consult.agent.domain.safety.SafetyCatalog;
import com.moonligh.consult.agent.domain.safety.SignalExtraction;
import com.moonligh.consult.agent.domain.safety.SignalExtractor;

/**
 * 调用 DeepSeek OpenAI 兼容接口抽取信号。不把原话写入日志。
 */
public class DeepSeekSignalExtractor implements SignalExtractor {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekSignalExtractor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SafetyProperties.Model settings;
    private final SafetyCatalog catalog;
    private final HttpClient http;
    private final Semaphore limiter;

    public DeepSeekSignalExtractor(SafetyProperties properties, SafetyCatalog catalog) {
        this.settings = properties.getModel();
        this.catalog = catalog;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(settings.getTimeoutMs()))
                .build();
        this.limiter = new Semaphore(settings.getMaxConcurrency());
    }

    /** 限流后调用一次。输出不合格时按配置再试一次。 */
    @Override
    public SignalExtraction extract(List<ConsultationMessage> transcript, Demographics demographics) {
        boolean acquired;
        try {
            // 同时调用不能超过 maxConcurrency。抢不到许可就算模型不可用。
            acquired = limiter.tryAcquire(settings.getTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return SignalExtraction.unavailable();
        }
        if (!acquired) {
            return SignalExtraction.unavailable();
        }
        try {
            SignalExtraction first = callOnce(transcript, demographics);
            if (first.status() == com.moonligh.consult.agent.domain.safety.ModelStatus.INVALID_OUTPUT
                    && settings.getInvalidOutputRetries() > 0) {
                return callOnce(transcript, demographics);
            }
            return first;
        } finally {
            limiter.release();
        }
    }

    /** 真正打一次 HTTP。没密钥、超时、4xx 都当成模型不可用。 */
    private SignalExtraction callOnce(List<ConsultationMessage> transcript, Demographics demographics) {
        if (settings.getApiKey() == null || settings.getApiKey().isBlank()) {
            log.error("model unavailable reason=missing-key");
            return SignalExtraction.unavailable();
        }
        try {
            String body = requestBody(transcript, demographics);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(settings.getBaseUrl()) + "/chat/completions"))
                    .timeout(Duration.ofMillis(settings.getTimeoutMs()))
                    .header("Authorization", "Bearer " + settings.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.error("model unavailable status={}", response.statusCode());
                return SignalExtraction.unavailable();
            }
            return parse(response.body());
        } catch (java.net.http.HttpTimeoutException error) {
            log.error("model timeout");
            return SignalExtraction.timeout();
        } catch (Exception error) {
            log.error("model unavailable type={}", error.getClass().getName());
            return SignalExtraction.unavailable();
        }
    }

    /** 从模型回复里抠 JSON。对不上原话格式就当输出不合格。 */
    private SignalExtraction parse(String body) throws Exception {
        JsonNode root = MAPPER.readTree(body);
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        if (content.isBlank()) {
            return SignalExtraction.invalid();
        }
        // 模型偶尔会在 JSON 前后加说明文字，只取第一个 { 到最后一个 }。
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end < start) {
            return SignalExtraction.invalid();
        }
        JsonNode payload = MAPPER.readTree(content.substring(start, end + 1));
        if (!payload.has("signals") || !payload.get("signals").isArray()) {
            return SignalExtraction.invalid();
        }
        List<ExtractedSignal> signals = new ArrayList<>();
        for (JsonNode node : payload.get("signals")) {
            if (!node.hasNonNull("type") || !node.has("messageSeq") || !node.hasNonNull("evidence")) {
                return SignalExtraction.invalid();
            }
            String type = node.get("type").asText();
            if (!catalog.hasSignal(type)) {
                continue;
            }
            signals.add(new ExtractedSignal(
                    type,
                    node.get("messageSeq").asInt(),
                    node.get("evidence").asText(),
                    node.path("temporal_status").asText("current"),
                    node.path("polarity").asText("positive"),
                    node.path("subject").asText("patient"),
                    node.path("uncertainty").asText("low")));
        }
        boolean unresolved = payload.path("unresolved_ambiguity").asBoolean(false);
        return new SignalExtraction(
                com.moonligh.consult.agent.domain.safety.ModelStatus.SUCCESS,
                settings.getModelName(),
                List.copyOf(signals),
                unresolved);
    }

    /** 拼请求体。原话带序号交给模型，日志里不打印这段。 */
    private String requestBody(List<ConsultationMessage> transcript, Demographics demographics) throws Exception {
        StringBuilder numbered = new StringBuilder();
        for (ConsultationMessage message : transcript) {
            numbered.append(message.seq()).append(':').append(message.text()).append('\n');
        }
        String allowed = String.join(",", catalog.decisionSignalTypes());
        String system = "只输出 JSON：{\"signals\":[{\"type\":\"\",\"messageSeq\":1,\"evidence\":\"\",\"polarity\":\"positive\",\"temporal_status\":\"current\",\"uncertainty\":\"low\"}],\"unresolved_ambiguity\":false}。"
                + "允许的 type：" + allowed + "。对不上这些 type 就 signals=[]，unresolved_ambiguity=false。"
                + "只转义目录别名对不上、但明显等于这些 type 的说法。"
                + "例如「心口像压了块大石头」type=chest_pain，evidence 用原句中的那几个字。"
                + "口角歪斜、口唇发子、胸口痛、压着痛、胸口闷已在影子目录，不要输出。"
                + "不得把普通咳嗽、胃痛、拉肚子、胃胀映射成 chest_pain 或 dyspnea。"
                + "不得编造未出现的急症。不得因不严重或好转删除当前阳性红旗。"
                + "evidence 必须是对应 seq 原话的子串。不要输出 JSON 之外的文字。";
        var root = MAPPER.createObjectNode();
        root.put("model", settings.getModelName());
        var messages = MAPPER.createArrayNode();
        messages.add(MAPPER.createObjectNode().put("role", "system").put("content", system));
        messages.add(MAPPER.createObjectNode().put("role", "user").put("content",
                "age=" + demographics.age() + " gender=" + demographics.gender() + "\n" + numbered));
        root.set("messages", messages);
        return MAPPER.writeValueAsString(root);
    }

    /** 去掉地址末尾的 /，避免拼出双斜杠。 */
    private static String trimSlash(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}
