package com.moonligh.consult.agent.infrastructure.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonligh.consult.agent.domain.safety.ConsultationMessage;
import com.moonligh.consult.agent.domain.safety.Demographics;
import com.moonligh.consult.agent.domain.safety.ExtractedSignal;
import com.moonligh.consult.agent.domain.safety.ModelStatus;
import com.moonligh.consult.agent.domain.safety.SafetyCatalog;
import com.moonligh.consult.agent.domain.safety.SignalExtraction;
import com.moonligh.consult.agent.domain.safety.SignalExtractor;

/**
 * 急症信号补漏。只拼提示词和解析，HTTP、限流、熔断交给 {@link ModelGateway}。整段对话上下文各异，不做缓存。
 */
public class DeepSeekSignalExtractor implements SignalExtractor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ModelGateway gateway;
    private final SafetyCatalog catalog;

    public DeepSeekSignalExtractor(ModelGateway gateway, SafetyCatalog catalog) {
        this.gateway = gateway;
        this.catalog = catalog;
    }

    /** 调一次；输出不合格时按配置再试。 */
    @Override
    public SignalExtraction extract(List<ConsultationMessage> transcript, Demographics demographics) {
        String system = systemPrompt();
        String user = userPrompt(transcript, demographics);
        int retries = gateway.lane(ModelPurpose.SAFETY).getInvalidOutputRetries();
        SignalExtraction result = callOnce(system, user);
        while (result.status() == ModelStatus.INVALID_OUTPUT && retries-- > 0) {
            result = callOnce(system, user);
        }
        return result;
    }

    private SignalExtraction callOnce(String system, String user) {
        ModelReply reply = gateway.chat(ModelPurpose.SAFETY, system, user, null, 0);
        if (reply.status() != ModelStatus.SUCCESS) {
            return new SignalExtraction(reply.status(), reply.modelName(), List.of(), false);
        }
        try {
            return parse(reply.content(), reply.modelName());
        } catch (Exception error) {
            return SignalExtraction.invalid();
        }
    }

    /** 从模型回复里抠 JSON。对不上格式就当输出不合格。 */
    private SignalExtraction parse(String content, String modelName) throws Exception {
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
        return new SignalExtraction(ModelStatus.SUCCESS, modelName, List.copyOf(signals), unresolved);
    }

    private String systemPrompt() {
        String allowed = String.join(",", catalog.decisionSignalTypes());
        return "只输出 JSON：{\"signals\":[{\"type\":\"\",\"messageSeq\":1,\"evidence\":\"\",\"polarity\":\"positive\",\"temporal_status\":\"current\",\"uncertainty\":\"low\"}],\"unresolved_ambiguity\":false}。"
                + "允许的 type：" + allowed + "。对不上这些 type 就 signals=[]，unresolved_ambiguity=false。"
                + "只转义目录别名对不上、但明显等于这些 type 的说法。"
                + "例如「心口像压了块大石头」type=chest_pain，evidence 用原句中的那几个字。"
                + "口角歪斜、口唇发子、胸口痛、压着痛、胸口闷已在影子目录，不要输出。"
                + "不得把普通咳嗽、胃痛、拉肚子、胃胀映射成 chest_pain 或 dyspnea。"
                + "不得编造未出现的急症。不得因不严重或好转删除当前阳性红旗。"
                + "evidence 必须是对应 seq 原话的子串。不要输出 JSON 之外的文字。";
    }

    private static String userPrompt(List<ConsultationMessage> transcript, Demographics demographics) {
        StringBuilder numbered = new StringBuilder();
        for (ConsultationMessage message : transcript) {
            numbered.append(message.seq()).append(':').append(message.text()).append('\n');
        }
        return "age=" + demographics.age() + " gender=" + demographics.gender() + "\n" + numbered;
    }
}
