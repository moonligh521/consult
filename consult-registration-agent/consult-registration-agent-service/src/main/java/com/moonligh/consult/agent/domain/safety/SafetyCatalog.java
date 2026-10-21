package com.moonligh.consult.agent.domain.safety;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 加载后不可变的急症目录。单次决策只使用这一个版本。
 */
public final class SafetyCatalog {

    public static final String URGENT_DEFAULT = "URGENT-DEFAULT";
    public static final String HUMAN_DEFAULT = "HUMAN-DEFAULT";
    public static final String FALLBACK_DEFAULT = "FALLBACK-DEFAULT";
    public static final String UNSUITABLE_DEFAULT = "UNSUITABLE-DEFAULT";

    private final String catalogVersion;
    private final String effectiveFrom;
    private final DraftMode draftMode;
    private final Map<String, SafetyTemplate> templates;
    private final List<SafetyRule> rules;

    public SafetyCatalog(
            String catalogVersion,
            String effectiveFrom,
            DraftMode draftMode,
            Map<String, SafetyTemplate> templates,
            List<SafetyRule> rules) {
        this.catalogVersion = catalogVersion;
        this.effectiveFrom = effectiveFrom;
        this.draftMode = draftMode;
        this.templates = Map.copyOf(templates);
        this.rules = List.copyOf(rules);
    }

    public String catalogVersion() {
        return catalogVersion;
    }

    public String effectiveFrom() {
        return effectiveFrom;
    }

    public DraftMode draftMode() {
        return draftMode;
    }

    public String templateText(String templateId) {
        if (templateId == null) {
            return templates.get(URGENT_DEFAULT).text();
        }
        SafetyTemplate template = templates.get(templateId);
        if (template == null) {
            return templates.get(URGENT_DEFAULT).text();
        }
        return template.text();
    }

    public boolean hasSignal(String signal) {
        for (SafetyRule rule : rules) {
            if (rule.signal().equals(signal)) {
                return true;
            }
        }
        return false;
    }

    public List<SafetyRule> rulesForEngine() {
        List<SafetyRule> loaded = new ArrayList<>();
        for (SafetyRule rule : rules) {
            if (rule.status() == RuleStatus.RETIRED) {
                continue;
            }
            loaded.add(rule);
        }
        return List.copyOf(loaded);
    }

    public List<SafetyRule> allRules() {
        return rules;
    }

    public boolean enforceDraft() {
        return draftMode == DraftMode.ENFORCE;
    }
}
