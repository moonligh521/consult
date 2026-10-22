package com.moonligh.consult.agent.domain.safety;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 加载后不可变的急症目录。单次决策只使用这一个版本。
 */
public final class SafetyCatalog {

    /** 急症默认提示。 */
    public static final String URGENT_DEFAULT = "URGENT-DEFAULT";
    /** 转人工默认提示。 */
    public static final String HUMAN_DEFAULT = "HUMAN-DEFAULT";
    /** 安全降级默认提示。 */
    public static final String FALLBACK_DEFAULT = "FALLBACK-DEFAULT";
    /** 渠道不适用默认提示。 */
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

    /** 按编号取提示正文。找不到就用急症默认文案。 */
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

    /** 目录里有没有这个信号名。模型抽出的 type 必须能对上。 */
    public boolean hasSignal(String signal) {
        for (SafetyRule rule : rules) {
            if (rule.signal().equals(signal)) {
                return true;
            }
        }
        return false;
    }

    /** 给规则引擎用的规则。已下线的不参与。 */
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

    /** 目录里全部规则，含已下线的，给自检用。 */
    public List<SafetyRule> allRules() {
        return rules;
    }

    /** 测试环境才会为 true。生产必须是 false。 */
    public boolean enforceDraft() {
        return draftMode == DraftMode.ENFORCE;
    }
}
