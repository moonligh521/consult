package com.moonligh.consult.agent.domain.safety;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 安全闸门：先用规则匹配，不够确定再让模型补漏。
 * 模型不能自己定最终状态。
 */
@Component
public class SafetyGate {

    private static final Logger log = LoggerFactory.getLogger(SafetyGate.class);

    private final SafetyCatalog catalog;
    private final RuleSafetyEngine engine;
    private final SignalExtractor extractor;

    public SafetyGate(SafetyCatalog catalog, RuleSafetyEngine engine, SignalExtractor extractor) {
        this.catalog = catalog;
        this.engine = engine;
        this.extractor = extractor;
    }

    /** 当前生效的急症目录。 */
    public SafetyCatalog catalog() {
        return catalog;
    }

    /** 评估全部原话。规则失败则走安全降级。 */
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
        SignalExtraction extraction = extractor.extract(transcript, demographics);
        if (extraction.status() == ModelStatus.DISABLED || extraction.status() == ModelStatus.SKIPPED) {
            return withModelStatus(rules, extraction.status());
        }
        if (extraction.status() != ModelStatus.SUCCESS) {
            // 模型超时或不可用：保留规则结论，并标成不确定。
            return fallbackFromModel(rules, extraction);
        }
        SafetyAssessment fromModel = fromModel(transcript, extraction);
        if (fromModel.undetermined() && fromModel.fallbackReason() == FallbackReason.MODEL_INVALID_OUTPUT) {
            return fallbackFromModel(rules, SignalExtraction.invalid());
        }
        // 规则和模型两边的阳性结论取并集，急症不会被模型否掉。
        return union(rules, fromModel, extraction.status());
    }

    /** 把模型抽出的信号对回目录规则。证据必须是原话子串。 */
    private SafetyAssessment fromModel(List<ConsultationMessage> transcript, SignalExtraction extraction) {
        boolean urgent = false;
        boolean prompt = false;
        boolean human = false;
        boolean unsuitable = false;
        boolean undetermined = extraction.unresolvedAmbiguity();
        String templateId = null;
        Set<String> matched = new LinkedHashSet<>();
        List<EvidenceRef> evidence = new ArrayList<>();
        for (ExtractedSignal signal : extraction.signals()) {
            ConsultationMessage message = messageAt(transcript, signal.messageSeq());
            // 证据对不上原话，或信号不在目录里，整次模型结果作废。
            if (message == null || signal.evidence() == null || !message.text().contains(signal.evidence())) {
                return invalidModel();
            }
            if (!catalog.hasSignal(signal.type())) {
                return invalidModel();
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

    /** 保留规则结论，只改模型状态。 */
    private static SafetyAssessment withModelStatus(SafetyAssessment assessment, ModelStatus status) {
        return new SafetyAssessment(
                assessment.urgent(),
                assessment.promptEvaluation(),
                assessment.humanRequested(),
                assessment.unsuitableForOnline(),
                assessment.undetermined(),
                assessment.fallbackReason(),
                assessment.templateId(),
                assessment.matchedRuleIds(),
                assessment.shadowRuleIds(),
                assessment.evidence(),
                status);
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

    /** 模型输出不合格：整次补漏作废。 */
    private static SafetyAssessment invalidModel() {
        return new SafetyAssessment(
                false, false, false, false, true, FallbackReason.MODEL_INVALID_OUTPUT, SafetyCatalog.FALLBACK_DEFAULT,
                List.of(), List.of(), List.of(), ModelStatus.INVALID_OUTPUT);
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
