package com.moonligh.consult.agent.domain.safety;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 安全闸门门面。规则先匹配，未达紧急阈值再模型补漏。模型不能设置最终状态。
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

    public SafetyCatalog catalog() {
        return catalog;
    }

    public SafetyAssessment assess(List<ConsultationMessage> transcript, Demographics demographics) {
        SafetyAssessment rules;
        try {
            rules = engine.evaluate(transcript, demographics, catalog, catalog.enforceDraft());
        } catch (RuntimeException error) {
            log.error("rule engine failed type={}", error.getClass().getName());
            return SafetyAssessment.engineFailure();
        }
        if (rules.urgent()) {
            return withModelStatus(rules, ModelStatus.SKIPPED);
        }
        SignalExtraction extraction = extractor.extract(transcript, demographics);
        if (extraction.status() == ModelStatus.DISABLED || extraction.status() == ModelStatus.SKIPPED) {
            return withModelStatus(rules, extraction.status());
        }
        if (extraction.status() != ModelStatus.SUCCESS) {
            return fallbackFromModel(rules, extraction);
        }
        SafetyAssessment fromModel = fromModel(transcript, extraction);
        if (fromModel.undetermined() && fromModel.fallbackReason() == FallbackReason.MODEL_INVALID_OUTPUT) {
            return fallbackFromModel(rules, SignalExtraction.invalid());
        }
        return union(rules, fromModel, extraction.status());
    }

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

    private SafetyRule ruleForSignal(String signal) {
        for (SafetyRule rule : catalog.rulesForEngine()) {
            if (rule.signal().equals(signal) && (rule.status() != RuleStatus.DRAFT || catalog.enforceDraft())) {
                return rule;
            }
        }
        return null;
    }

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

    private static ConsultationMessage messageAt(List<ConsultationMessage> transcript, int seq) {
        for (ConsultationMessage message : transcript) {
            if (message.seq() == seq) {
                return message;
            }
        }
        return null;
    }

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

    private static SafetyAssessment invalidModel() {
        return new SafetyAssessment(
                false, false, false, false, true, FallbackReason.MODEL_INVALID_OUTPUT, SafetyCatalog.FALLBACK_DEFAULT,
                List.of(), List.of(), List.of(), ModelStatus.INVALID_OUTPUT);
    }

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
