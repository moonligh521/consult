package com.moonligh.consult.agent.domain.safety;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 从 classpath JSON 加载目录。校验失败则抛 CatalogException。
 */
public class CatalogLoader {

    public static final String DEFAULT_LOCATION = "/safety/urgent-catalog.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> REQUIRED_TEMPLATES = Set.of(
            SafetyCatalog.URGENT_DEFAULT,
            SafetyCatalog.HUMAN_DEFAULT,
            SafetyCatalog.FALLBACK_DEFAULT,
            SafetyCatalog.UNSUITABLE_DEFAULT);

    public static SafetyCatalog loadDefault(DraftMode draftMode) {
        return load(DEFAULT_LOCATION, draftMode);
    }

    public static SafetyCatalog load(String classpathLocation, DraftMode draftMode) {
        InputStream input = CatalogLoader.class.getResourceAsStream(classpathLocation);
        if (input == null) {
            throw new CatalogException("找不到急症目录: " + classpathLocation);
        }
        try (input) {
            return load(input, draftMode);
        } catch (CatalogException error) {
            throw error;
        } catch (Exception error) {
            throw new CatalogException("急症目录无法读取", error);
        }
    }

    public static SafetyCatalog load(InputStream input, DraftMode draftMode) {
        JsonNode root;
        try {
            root = MAPPER.readTree(input);
        } catch (Exception error) {
            throw new CatalogException("急症目录 JSON 无效", error);
        }
        String version = text(root, "catalogVersion");
        String effectiveFrom = text(root, "effectiveFrom");
        if (version.isBlank() || effectiveFrom.isBlank()) {
            throw new CatalogException("目录缺少 catalogVersion 或 effectiveFrom");
        }
        Map<String, SafetyTemplate> templates = readTemplates(root.get("templates"));
        for (String required : REQUIRED_TEMPLATES) {
            if (!templates.containsKey(required)) {
                throw new CatalogException("目录缺少模板 " + required);
            }
        }
        List<SafetyRule> rules = readRules(root.get("rules"), templates);
        SafetyCatalog catalog = new SafetyCatalog(version, effectiveFrom, draftMode, templates, rules);
        CatalogSelfTest.run(catalog);
        return catalog;
    }

    public static void assertDraftModeAllowed(DraftMode draftMode, String[] activeProfiles) {
        if (draftMode != DraftMode.ENFORCE) {
            return;
        }
        if (activeProfiles != null) {
            for (String profile : activeProfiles) {
                if ("test".equals(profile)) {
                    return;
                }
            }
        }
        throw new CatalogException("draft-mode=ENFORCE 只允许 test profile");
    }

    private static Map<String, SafetyTemplate> readTemplates(JsonNode array) {
        Map<String, SafetyTemplate> templates = new HashMap<>();
        if (array == null || !array.isArray()) {
            throw new CatalogException("目录缺少 templates");
        }
        for (JsonNode node : array) {
            String id = text(node, "id");
            String body = text(node, "text");
            RuleStatus status = parseRuleStatus(text(node, "reviewStatus"), "reviewStatus");
            if (id.isBlank() || body.isBlank()) {
                throw new CatalogException("模板 id 或 text 为空");
            }
            if (templates.containsKey(id)) {
                throw new CatalogException("模板 id 重复: " + id);
            }
            templates.put(id, new SafetyTemplate(id, body, status));
        }
        return templates;
    }

    private static List<SafetyRule> readRules(JsonNode array, Map<String, SafetyTemplate> templates) {
        if (array == null || !array.isArray()) {
            throw new CatalogException("目录缺少 rules");
        }
        List<SafetyRule> rules = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Map<RuleStatus, Set<String>> aliasesByStatus = new HashMap<>();
        for (JsonNode node : array) {
            SafetyRule rule = readRule(node, templates);
            if (!ids.add(rule.ruleId())) {
                throw new CatalogException("ruleId 重复: " + rule.ruleId());
            }
            Set<String> aliases = aliasesByStatus.computeIfAbsent(rule.status(), key -> new HashSet<>());
            for (String alias : rule.aliases()) {
                if (!aliases.add(alias)) {
                    throw new CatalogException("同一状态下别名重复: " + alias);
                }
            }
            rules.add(rule);
        }
        return rules;
    }

    private static SafetyRule readRule(JsonNode node, Map<String, SafetyTemplate> templates) {
        String ruleId = text(node, "ruleId");
        String ruleVersion = text(node, "ruleVersion");
        RuleStatus status = parseRuleStatus(text(node, "status"), "status");
        String templateId = text(node, "patientMessageTemplateId");
        List<String> aliases = strings(node.get("aliases"));
        if (ruleId.isBlank() || aliases.isEmpty()) {
            throw new CatalogException("规则缺少 ruleId 或 aliases");
        }
        if (!templates.containsKey(templateId)) {
            throw new CatalogException("规则 " + ruleId + " 引用了不存在的模板 " + templateId);
        }
        if (status == RuleStatus.APPROVED) {
            if (text(node, "clinicalReviewer").isBlank()
                    || text(node, "reviewDate").isBlank()
                    || text(node, "evidenceReference").isBlank()) {
                throw new CatalogException("APPROVED 规则缺少审核字段: " + ruleId);
            }
        }
        return new SafetyRule(
                ruleId,
                ruleVersion,
                status,
                text(node, "clinicalCategory"),
                text(node, "signal"),
                aliases,
                strings(node.get("requiredContext")),
                strings(node.get("exclusionConditions")),
                parsePolicyAction(text(node, "negatedPolicy"), "negatedPolicy"),
                parsePolicyAction(text(node, "uncertaintyPolicy"), "uncertaintyPolicy"),
                parsePolicyAction(text(node, "resolvedPolicy"), "resolvedPolicy"),
                parsePolicyAction(text(node, "missingContextPolicy"), "missingContextPolicy"),
                parseDisposition(text(node, "disposition"), "disposition"),
                templateId,
                strings(node.get("positiveCases")),
                strings(node.get("negativeCases")),
                blankToNull(text(node, "clinicalReviewer")),
                blankToNull(text(node, "reviewDate")),
                blankToNull(text(node, "evidenceReference")));
    }

    private static List<String> strings(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new CatalogException("期望数组");
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String value = item.asText("").trim();
            if (value.isEmpty()) {
                throw new CatalogException("数组元素不能为空");
            }
            values.add(value);
        }
        return List.copyOf(values);
    }

    private static RuleStatus parseRuleStatus(String raw, String field) {
        try {
            return RuleStatus.valueOf(raw);
        } catch (IllegalArgumentException error) {
            throw new CatalogException("非法 " + field + ": " + raw);
        }
    }

    private static PolicyAction parsePolicyAction(String raw, String field) {
        try {
            return PolicyAction.valueOf(raw);
        } catch (IllegalArgumentException error) {
            throw new CatalogException("非法 " + field + ": " + raw);
        }
    }

    private static Disposition parseDisposition(String raw, String field) {
        try {
            return Disposition.valueOf(raw);
        } catch (IllegalArgumentException error) {
            throw new CatalogException("非法 " + field + ": " + raw);
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return "";
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        return value.asText("");
    }

    private static String blankToNull(String value) {
        return value.isBlank() ? null : value;
    }
}
