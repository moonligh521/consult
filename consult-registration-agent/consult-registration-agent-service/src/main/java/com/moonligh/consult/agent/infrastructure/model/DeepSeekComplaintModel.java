package com.moonligh.consult.agent.infrastructure.model;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonligh.consult.agent.api.enums.Gender;
import com.moonligh.consult.agent.domain.CompactText;
import com.moonligh.consult.agent.domain.department.DepartmentCatalog;
import com.moonligh.consult.framework.model.ModelGateway;
import com.moonligh.consult.framework.model.ModelPurpose;
import com.moonligh.consult.framework.model.ModelReply;
import com.moonligh.consult.framework.model.ModelStatus;
import com.moonligh.consult.agent.domain.triage.ComplaintCanon;
import com.moonligh.consult.agent.domain.triage.ComplaintModel;
import com.moonligh.consult.agent.domain.triage.ComplaintReply;
import com.moonligh.consult.agent.domain.triage.TriageEngine;

/**
 * 主诉模型：一次调用给出病名归约与挂科建议。只解析格式，允许列表与证据子串由引擎校验。
 * 只缓存格式合格的回复；键含提示词版本和目录版本，目录一变自然失效。
 */
public class DeepSeekComplaintModel implements ComplaintModel {

    public static final String PROMPT_VERSION = "complaint-v3.1";
    static final String PROMPT_LOCATION = "/model/complaint-prompt.txt";
    private static final String PROMPT_TEMPLATE = loadPrompt();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_TOKENS = 2000;
    private static final String RETRY_HINT = "\n只输出合法 JSON。症状不要填进 terms，terms 只能是允许病名或 []。";

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
        var lane = gateway.lane(ModelPurpose.COMPLAINT);
        ComplaintReply reply = callOnce(system, user, key, allowTerms);
        int timeoutRetries = lane.getTimeoutRetries();
        if (reply.status() == ModelStatus.TIMEOUT && timeoutRetries > 0) {
            reply = callOnce(system, user, key, allowTerms);
        }
        int invalidRetries = lane.getInvalidOutputRetries();
        while (reply.status() == ModelStatus.INVALID_OUTPUT && invalidRetries-- > 0) {
            reply = callOnce(system, user + RETRY_HINT, key, allowTerms);
        }
        return reply;
    }

    private ComplaintReply callOnce(String system, String user, String key, List<String> allowTerms) {
        ModelReply raw = gateway.chat(ModelPurpose.COMPLAINT, system, user, key, MAX_TOKENS);
        if (raw.status() != ModelStatus.SUCCESS) {
            return ComplaintReply.failed(raw.status(), raw.latencyMs(), PROMPT_VERSION, raw.modelName());
        }
        String json = extractJson(raw.content());
        ComplaintReply parsed = json == null ? null : parse(json, raw);
        if (parsed == null) {
            return ComplaintReply.failed(ModelStatus.INVALID_OUTPUT, raw.latencyMs(), PROMPT_VERSION, raw.modelName());
        }
        ComplaintReply usable = stripIllegalTerms(parsed, allowTerms);
        if (usable.terms().isEmpty()
                && (usable.specialty() == null || usable.specialty().isBlank())) {
            return ComplaintReply.failed(
                    ModelStatus.INVALID_OUTPUT, raw.latencyMs(), PROMPT_VERSION, raw.modelName());
        }
        if (!raw.cached()) {
            gateway.remember(key, json);
        }
        return usable;
    }

    /** 不在允许列表的病名丢掉；挂科字段原样留下，交给引擎再校验。 */
    static ComplaintReply stripIllegalTerms(ComplaintReply reply, List<String> allowTerms) {
        if (reply == null) {
            return null;
        }
        Set<String> allow = new HashSet<>(allowTerms == null ? List.of() : allowTerms);
        List<ComplaintReply.ModelTerm> kept = new ArrayList<>();
        for (ComplaintReply.ModelTerm term : reply.terms()) {
            if (term.term() == null || term.term().isBlank() || !allow.contains(term.term())) {
                continue;
            }
            kept.add(term);
            if (kept.size() >= ComplaintCanon.MAX_TERMS) {
                break;
            }
        }
        return new ComplaintReply(
                reply.status(),
                kept,
                reply.specialty(),
                reply.specialtyEvidence(),
                reply.latencyMs(),
                reply.cached(),
                reply.promptVersion(),
                reply.modelName());
    }

    /** 病名必须在允许列表。调用方用这个判断要不要丢掉某一条。 */
    static boolean termsAllowed(ComplaintReply reply, List<String> allowTerms) {
        if (reply == null || reply.terms().size() > ComplaintCanon.MAX_TERMS) {
            return false;
        }
        Set<String> allow = new HashSet<>(allowTerms == null ? List.of() : allowTerms);
        for (ComplaintReply.ModelTerm term : reply.terms()) {
            if (term.term() == null || term.term().isBlank() || !allow.contains(term.term())) {
                return false;
            }
        }
        return true;
    }

    private static ComplaintReply parse(String json, ModelReply raw) {
        try {
            JsonNode payload = MAPPER.readTree(json);
            JsonNode terms = payload.get("terms");
            if (terms != null && !terms.isNull() && !terms.isArray()) {
                return null;
            }
            List<ComplaintReply.ModelTerm> list = new ArrayList<>();
            if (terms != null && terms.isArray()) {
                for (JsonNode node : terms) {
                    if (node == null || !node.hasNonNull("term") || node.get("term").asText().isBlank()) {
                        continue;
                    }
                    if (!node.hasNonNull("evidence") || node.get("evidence").asText().isBlank()) {
                        continue;
                    }
                    list.add(new ComplaintReply.ModelTerm(node.get("term").asText(), node.get("evidence").asText()));
                }
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

    static String systemPrompt(List<String> allowTerms, List<String> candidates) {
        return PROMPT_TEMPLATE
                .replace("{{ALLOW_TERMS}}", String.join("、", allowTerms == null ? List.of() : allowTerms))
                .replace("{{CANDIDATES}}", String.join("、", candidates == null ? List.of() : candidates));
    }

    private static String loadPrompt() {
        try (InputStream in = DeepSeekComplaintModel.class.getResourceAsStream(PROMPT_LOCATION)) {
            if (in == null) {
                throw new IllegalStateException("找不到主诉提示词 " + PROMPT_LOCATION);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("读取主诉提示词失败", error);
        }
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
