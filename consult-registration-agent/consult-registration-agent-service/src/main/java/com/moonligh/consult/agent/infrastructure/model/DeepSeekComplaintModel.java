package com.moonligh.consult.agent.infrastructure.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonligh.consult.agent.api.enums.Gender;
import com.moonligh.consult.agent.domain.CompactText;
import com.moonligh.consult.agent.domain.department.DepartmentCatalog;
import com.moonligh.consult.agent.domain.safety.ModelStatus;
import com.moonligh.consult.agent.domain.triage.ComplaintModel;
import com.moonligh.consult.agent.domain.triage.ComplaintReply;
import com.moonligh.consult.agent.domain.triage.TriageEngine;

/**
 * 主诉模型：一次调用给出病名归约与挂科建议。只解析格式，允许列表与证据子串由引擎校验。
 * 只缓存格式合格的回复；键含提示词版本和目录版本，目录一变自然失效。
 */
public class DeepSeekComplaintModel implements ComplaintModel {

    public static final String PROMPT_VERSION = "complaint-v1";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_TOKENS = 300;

    private final ModelGateway gateway;
    private final DepartmentCatalog catalog;

    public DeepSeekComplaintModel(ModelGateway gateway, DepartmentCatalog catalog) {
        this.gateway = gateway;
        this.catalog = catalog;
    }

    @Override
    public ComplaintReply interpret(
            String description,
            Integer age,
            Gender gender,
            List<String> allowTerms,
            List<String> candidates) {
        String system = systemPrompt(allowTerms, candidates);
        String user = "age=" + (age == null ? "未知" : age) + " gender=" + (gender == null ? "未知" : gender.name())
                + "\n主诉：" + description;
        String key = cacheKey(description, age, gender, candidates);
        int retries = gateway.lane(ModelPurpose.COMPLAINT).getInvalidOutputRetries();
        ComplaintReply reply = callOnce(system, user, key);
        while (reply.status() == ModelStatus.INVALID_OUTPUT && retries-- > 0) {
            reply = callOnce(system, user, key);
        }
        return reply;
    }

    private ComplaintReply callOnce(String system, String user, String key) {
        ModelReply raw = gateway.chat(ModelPurpose.COMPLAINT, system, user, key, MAX_TOKENS);
        if (raw.status() != ModelStatus.SUCCESS) {
            return ComplaintReply.failed(raw.status(), raw.latencyMs(), PROMPT_VERSION, raw.modelName());
        }
        String json = extractJson(raw.content());
        ComplaintReply parsed = json == null ? null : parse(json, raw);
        if (parsed == null) {
            return ComplaintReply.failed(ModelStatus.INVALID_OUTPUT, raw.latencyMs(), PROMPT_VERSION, raw.modelName());
        }
        if (!raw.cached()) {
            gateway.remember(key, json);
        }
        return parsed;
    }

    private static ComplaintReply parse(String json, ModelReply raw) {
        try {
            JsonNode payload = MAPPER.readTree(json);
            JsonNode terms = payload.get("terms");
            if (terms == null || !terms.isArray()) {
                return null;
            }
            List<ComplaintReply.ModelTerm> list = new ArrayList<>();
            for (JsonNode node : terms) {
                if (!node.hasNonNull("term") || !node.hasNonNull("evidence")) {
                    return null;
                }
                list.add(new ComplaintReply.ModelTerm(node.get("term").asText(), node.get("evidence").asText()));
            }
            String specialty = payload.hasNonNull("specialty") ? payload.get("specialty").asText() : null;
            String evidence = payload.hasNonNull("specialtyEvidence") ? payload.get("specialtyEvidence").asText() : null;
            return new ComplaintReply(ModelStatus.SUCCESS, list, specialty, evidence, raw.latencyMs(), raw.cached(),
                    PROMPT_VERSION, raw.modelName());
        } catch (Exception error) {
            return null;
        }
    }

    private static String extractJson(String content) {
        if (content == null) {
            return null;
        }
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        return start < 0 || end < start ? null : content.substring(start, end + 1);
    }

    private static String systemPrompt(List<String> allowTerms, List<String> candidates) {
        return "你是互联网医院导诊。根据患者主诉，只输出 JSON："
                + "{\"terms\":[{\"term\":\"\",\"evidence\":\"\"}],\"specialty\":\"\",\"specialtyEvidence\":\"\"}。"
                + "terms：患者当前这次想看的病，最多 2 个，term 只能从允许病名中原样选；"
                + "只凭症状推测、没有足够把握时 terms=[]，不要猜。既往史、家人的病不算。"
                + "evidence 必须是主诉原文中的连续子串。"
                + "specialty：从候选科室中原样选一个最合适的；拿不准就选全科。"
                + "specialtyEvidence 是支撑它的原文子串，可为空。不要输出 JSON 之外的文字。"
                + "\n允许病名：" + String.join("、", allowTerms)
                + "\n候选科室：" + String.join("、", candidates);
    }

    private String cacheKey(String description, Integer age, Gender gender, List<String> candidates) {
        String raw = CompactText.of(description) + "|" + TriageEngine.ageBand(age) + "|"
                + (gender == null ? "" : gender.name()) + "|" + String.join(",", candidates);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String hash = HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
            return "consult:triage:model:" + PROMPT_VERSION + ":" + catalog.snapshot().version() + ":" + hash;
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
