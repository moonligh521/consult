package com.moonligh.consult.agent.domain.safety;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.moonligh.consult.agent.domain.CompactText;

/**
 * 安全闸门：先用规则匹配，不够确定再让模型补漏。
 * 模型不能自己定最终状态。
 */
public class SafetyGate {

    private static final Logger log = LoggerFactory.getLogger(SafetyGate.class);

    private final SafetyCatalog catalog;
    private final RuleSafetyEngine engine;
    private final SignalExtractor extractor;
    private final ModelTriggerScan triggers;

    public SafetyGate(SafetyCatalog catalog, RuleSafetyEngine engine, SignalExtractor extractor) {
        this(catalog, engine, extractor, ModelTriggerScan.load());
    }

    public SafetyGate(SafetyCatalog catalog, RuleSafetyEngine engine, SignalExtractor extractor, ModelTriggerScan triggers) {
        this.catalog = catalog;
        this.engine = engine;
        this.extractor = extractor;
        this.triggers = triggers;
    }

    /** 当前生效的急症目录。 */
    public SafetyCatalog catalog() {
        return catalog;
    }

    /**
     * 评估全部原话。三层：规则急症钉死则 SKIPPED；命中触发词才走 SAFETY 模型；模型失败走 SAFE_FALLBACK。
     */
    public SafetyAssessment assess(List<ConsultationMessage> transcript, Demographics demographics) {
        SafetyAssessment rules;
        try {
            rules = engine.evaluate(transcript, demographics, catalog, catalog.enforceDraft());
        } catch (RuntimeException error) {
            // 规则引擎自己挂了：安全降级，不要继续往下跑。
            log.error("rule engine failed type={}", error.getClass().getName());
            return SafetyAssessment.engineFailure();
        }
        // 规则已经判急症，不再调用模型，避免模型把急症说成否定。
        if (rules.urgent()) {
            return withModelStatus(rules, ModelStatus.SKIPPED);
        }
        List<String> hits = triggers.scan(transcript);
        // 没碰到触发组合、没有影子命中、规则也没说拿不准：规则已能定「无风险」。
        if (hits.isEmpty() && rules.shadowRuleIds().isEmpty() && !rules.undetermined()) {
            return withModelStatus(rules, ModelStatus.SKIPPED);
        }
        SignalExtraction extraction = extractor.extract(transcript, demographics);
        if (extraction.status() == ModelStatus.DISABLED || extraction.status() == ModelStatus.SKIPPED) {
            return rules.withModel(extraction.status(), hits, false);
        }
        if (extraction.status() != ModelStatus.SUCCESS) {
            // 已触发但模型拿不到结果：保留规则结论，标成不确定，走安全降级。
            return fallbackFromModel(rules, extraction).withModel(extraction.status(), hits, false);
        }
        SafetyAssessment fromModel = fromModel(transcript, extraction);
        if (fromModel.undetermined() && fromModel.fallbackReason() == FallbackReason.MODEL_INVALID_OUTPUT) {
            return fallbackFromModel(rules, SignalExtraction.invalid()).withModel(ModelStatus.INVALID_OUTPUT, hits, false);
        }
        // 规则和模型两边的阳性结论取并集，急症不会被模型否掉。
        return union(rules, fromModel, extraction.status()).withModel(extraction.status(), hits, false);
    }

    /** 把模型抽出的信号对回目录规则。证据必须是原话子串。 */
    private SafetyAssessment fromModel(List<ConsultationMessage> transcript, SignalExtraction extraction) {
        boolean urgent = false;
        boolean prompt = false;
        boolean human = false;
        boolean unsuitable = false;
        boolean undetermined = false;
        String templateId = null;
        Set<String> matched = new LinkedHashSet<>();
        List<EvidenceRef> evidence = new ArrayList<>();
        for (ExtractedSignal signal : extraction.signals()) {
            ConsultationMessage message = messageAt(transcript, signal.messageSeq());
            // 证据对不上原话：跳过这条信号，不要把整单打成安全降级。
            if (message == null || signal.evidence() == null || signal.evidence().isBlank()
                    || !message.text().contains(signal.evidence())) {
                continue;
            }
            // 未知 type 跳过，不要把整单打成安全降级。
            if (!catalog.hasSignal(signal.type())) {
                continue;
            }
            // 影子目录已经记下的说法，不能靠模型改写成基线急症。
            if (coveredByDraftAlias(message.text()) && !coveredByDecisionAlias(message.text())) {
                continue;
            }
            SafetyRule rule = ruleForSignal(signal.type());
            if (rule == null) {
                continue;
            }
            boolean shadow = rule.status() == RuleStatus.DRAFT && !catalog.enforceDraft();
            PolicyAction action = actionFromModel(rule, signal);
            int start = message.text().indexOf(signal.evidence());
            EvidenceRef ref = new EvidenceRef(signal.messageSeq(), start, start + signal.evidence().length());
            if (shadow) {
                continue;
            }
            if (action == PolicyAction.SUPPRESS) {
                continue;
            }
            matched.add(rule.ruleId());
            evidence.add(ref);
            if (action == PolicyAction.UNDETERMINED) {
                undetermined = true;
                continue;
            }
            switch (rule.disposition()) {
                case URGENT -> {
                    urgent = true;
                    templateId = rule.patientMessageTemplateId();
                }
                case HUMAN -> human = true;
                case UNSUITABLE_FOR_ONLINE -> unsuitable = true;
                case PROMPT_EVALUATION -> prompt = true;
                case PROMPT_EVALUATION_BLOCK_ONLINE -> {
                    prompt = true;
                    unsuitable = true;
                }
                case NO_ESCALATION -> {
                }
            }
        }
        return new SafetyAssessment(
                urgent, prompt, human, unsuitable, undetermined, null, templateId,
                List.copyOf(matched), List.of(), List.copyOf(evidence), ModelStatus.SUCCESS);
    }

    /** 原话已经能被影子别名对上。 */
    private boolean coveredByDraftAlias(String text) {
        return coveredByAlias(text, RuleStatus.DRAFT);
    }

    /** 原话已经能被生产别名对上。 */
    private boolean coveredByDecisionAlias(String text) {
        return coveredByAlias(text, RuleStatus.BASELINE) || coveredByAlias(text, RuleStatus.APPROVED);
    }

    /** 紧凑原话里有没有指定状态规则的别名。 */
    private boolean coveredByAlias(String text, RuleStatus status) {
        String compact = CompactText.of(text);
        if (compact.isEmpty()) {
            return false;
        }
        for (SafetyRule rule : catalog.rulesForEngine()) {
            if (rule.status() != status) {
                continue;
            }
            for (String alias : rule.aliases()) {
                String compactAlias = CompactText.of(alias);
                if (!compactAlias.isEmpty() && compact.contains(compactAlias)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 按信号名找到目录规则。DRAFT 默认不参与决策。 */
    private SafetyRule ruleForSignal(String signal) {
        for (SafetyRule rule : catalog.rulesForEngine()) {
            if (rule.signal().equals(signal) && (rule.status() != RuleStatus.DRAFT || catalog.enforceDraft())) {
                return rule;
            }
        }
        return null;
    }

    /** 按模型给的极性、时间和不确定度，套规则里的策略。 */
    private PolicyAction actionFromModel(SafetyRule rule, ExtractedSignal signal) {
        String polarity = signal.polarity() == null ? "" : signal.polarity();
        String temporal = signal.temporalStatus() == null ? "" : signal.temporalStatus();
        String uncertainty = signal.uncertainty() == null ? "" : signal.uncertainty();
        if ("unclear".equals(polarity) || "unknown".equals(temporal) || "high".equals(uncertainty)) {
            return rule.uncertaintyPolicy();
        }
        if ("negated".equals(polarity)) {
            return rule.negatedPolicy();
        }
        if ("resolved".equals(temporal) || "past".equals(temporal)) {
            return rule.resolvedPolicy();
        }
        return PolicyAction.ESCALATE;
    }

    /** 按序号找到那条原话。 */
    private static ConsultationMessage messageAt(List<ConsultationMessage> transcript, int seq) {
        for (ConsultationMessage message : transcript) {
            if (message.seq() == seq) {
                return message;
            }
        }
        return null;
    }

    /** 保留规则结论，只改模型状态。规则自己定了。 */
    private static SafetyAssessment withModelStatus(SafetyAssessment assessment, ModelStatus status) {
        return assessment.withModel(status, List.of(), true);
    }

    /** 模型失败时保留规则结论，并标成不确定。 */
    private static SafetyAssessment fallbackFromModel(SafetyAssessment rules, SignalExtraction extraction) {
        FallbackReason reason = extraction.status() == ModelStatus.INVALID_OUTPUT
                ? FallbackReason.MODEL_INVALID_OUTPUT
                : FallbackReason.MODEL_UNAVAILABLE;
        return new SafetyAssessment(
                rules.urgent(),
                rules.promptEvaluation(),
                rules.humanRequested(),
                rules.unsuitableForOnline(),
                true,
                reason,
                SafetyCatalog.FALLBACK_DEFAULT,
                rules.matchedRuleIds(),
                rules.shadowRuleIds(),
                rules.evidence(),
                extraction.status());
    }

    /** 规则和模型的阳性结论取并集。急症不会被模型否掉。 */
    private static SafetyAssessment union(SafetyAssessment rules, SafetyAssessment model, ModelStatus status) {
        Set<String> matched = new LinkedHashSet<>(rules.matchedRuleIds());
        matched.addAll(model.matchedRuleIds());
        Set<String> shadows = new LinkedHashSet<>(rules.shadowRuleIds());
        shadows.addAll(model.shadowRuleIds());
        List<EvidenceRef> evidence = new ArrayList<>(rules.evidence());
        evidence.addAll(model.evidence());
        String templateId = rules.templateId() != null ? rules.templateId() : model.templateId();
        return new SafetyAssessment(
                rules.urgent() || model.urgent(),
                rules.promptEvaluation() || model.promptEvaluation(),
                rules.humanRequested() || model.humanRequested(),
                rules.unsuitableForOnline() || model.unsuitableForOnline(),
                rules.undetermined() || model.undetermined(),
                model.fallbackReason() != null ? model.fallbackReason() : rules.fallbackReason(),
                templateId,
                List.copyOf(matched),
                List.copyOf(shadows),
                List.copyOf(evidence),
                status);
    }
}
