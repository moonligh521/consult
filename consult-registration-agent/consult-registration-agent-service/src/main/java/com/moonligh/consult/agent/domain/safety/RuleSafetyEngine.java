package com.moonligh.consult.agent.domain.safety;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 按急症目录匹配患者原话。
 * 步骤：归一 → 分句 → 找别名 → 看否定/时间 → 按规则写出处置。
 * 模型不能调用本类去设置最终状态。
 */
public class RuleSafetyEngine {

    public static final String VERSION = "1.0.0";

    private static final String[] SPLITTERS = {"但是", "不过", "可是", "然而", "，", "。", "；", "！", "？", ",", ".", ";", "!", "?", "\n"};
    private static final String[] DOUBLE_NEGATIONS = {"不是没有", "没有不", "并非没有"};
    private static final String[] UNCERTAIN_WORDS = {"是不是", "会不会", "好像", "可能", "不确定", "说不清"};
    private static final String[] NEGATIONS = {"没有", "并非", "不是", "否认", "没", "无", "不", "未"};
    private static final String[] RESOLVED_WORDS = {"已经好了", "缓解了", "不疼了", "消失了", "现在没事"};
    private static final String[] CURRENT_ANAPHORA = {"现在开始痛了", "现在开始疼了", "又痛了", "又疼了"};

    public SafetyAssessment evaluate(
            List<ConsultationMessage> transcript,
            Demographics demographics,
            SafetyCatalog catalog,
            boolean includeDraftInDecision) {
        List<Hit> hits = new ArrayList<>();
        for (ConsultationMessage message : transcript) {
            collectHits(message.seq(), message.text(), catalog, includeDraftInDecision, hits);
        }
        Concatenation concatenation = concatenate(transcript);
        collectHits(concatenation.joinedSeq, concatenation.text, catalog, includeDraftInDecision, hits);
        applyAnaphora(transcript, catalog, includeDraftInDecision, hits);
        return toAssessment(hits, demographics);
    }

    private void collectHits(
            int evidenceSeq,
            String original,
            SafetyCatalog catalog,
            boolean includeDraftInDecision,
            List<Hit> hits) {
        Normalized normalized = Normalized.of(original);
        List<Sentence> sentences = split(normalized);
        for (SafetyRule rule : catalog.rulesForEngine()) {
            if (rule.status() == RuleStatus.DRAFT && !includeDraftInDecision) {
                collectDraftShadows(rule, sentences, evidenceSeq, hits);
                continue;
            }
            for (Sentence sentence : sentences) {
                hits.addAll(hitsInSentence(rule, sentence, evidenceSeq, false));
            }
        }
    }

    private void collectDraftShadows(SafetyRule rule, List<Sentence> sentences, int evidenceSeq, List<Hit> hits) {
        for (Sentence sentence : sentences) {
            hits.addAll(hitsInSentence(rule, sentence, evidenceSeq, true));
        }
    }

    private List<Hit> hitsInSentence(SafetyRule rule, Sentence sentence, int evidenceSeq, boolean shadow) {
        List<Match> matches = aliasMatches(rule, sentence);
        List<Hit> hits = new ArrayList<>();
        for (Match match : matches) {
            PolicyAction action = actionOf(rule, sentence, match);
            hits.add(new Hit(rule, action, new EvidenceRef(evidenceSeq, match.originalStart, match.originalEnd), shadow));
        }
        return hits;
    }

    private List<Match> aliasMatches(SafetyRule rule, Sentence sentence) {
        List<Match> matches = new ArrayList<>();
        String sentenceText = sentence.normalized();
        boolean[] taken = new boolean[sentenceText.length()];
        List<String> aliases = new ArrayList<>(rule.aliases());
        aliases.sort(Comparator.comparingInt(String::length).reversed());
        for (String alias : aliases) {
            String needle = Normalized.of(alias).text;
            int from = 0;
            while (from <= sentenceText.length() - needle.length()) {
                int at = sentenceText.indexOf(needle, from);
                if (at < 0) {
                    break;
                }
                if (!occupied(taken, at, at + needle.length())) {
                    mark(taken, at, at + needle.length());
                    int originalStart = sentence.originalIndex(at);
                    int originalEnd = sentence.originalIndex(at + needle.length() - 1) + 1;
                    matches.add(new Match(at, at + needle.length(), originalStart, originalEnd));
                }
                from = at + 1;
            }
        }
        return matches;
    }

    private PolicyAction actionOf(SafetyRule rule, Sentence sentence, Match match) {
        String sentenceText = sentence.normalized();
        String window = preceding(sentenceText, match.normalizedStart, 6);
        boolean unclear = containsAny(sentenceText, DOUBLE_NEGATIONS) || containsAny(sentenceText, UNCERTAIN_WORDS);
        boolean negated = containsAny(window, NEGATIONS);
        boolean resolved = containsAny(sentenceText, RESOLVED_WORDS);
        PolicyAction hitAction;
        if (unclear) {
            hitAction = rule.uncertaintyPolicy();
        } else if (negated) {
            hitAction = rule.negatedPolicy();
        } else if (resolved) {
            hitAction = rule.resolvedPolicy();
        } else {
            hitAction = PolicyAction.ESCALATE;
        }
        return hitAction;
    }

    private void applyAnaphora(
            List<ConsultationMessage> transcript,
            SafetyCatalog catalog,
            boolean includeDraftInDecision,
            List<Hit> hits) {
        if (transcript.size() < 2) {
            return;
        }
        String later = transcript.get(transcript.size() - 1).text();
        if (!containsAny(later, CURRENT_ANAPHORA)) {
            return;
        }
        for (int i = 0; i < transcript.size() - 1; i++) {
            ConsultationMessage earlier = transcript.get(i);
            for (SafetyRule rule : catalog.rulesForEngine()) {
                boolean shadow = rule.status() == RuleStatus.DRAFT && !includeDraftInDecision;
                Normalized normalized = Normalized.of(earlier.text());
                for (Sentence sentence : split(normalized)) {
                    List<Match> matches = aliasMatches(rule, sentence);
                    for (Match match : matches) {
                        hits.add(new Hit(rule, PolicyAction.ESCALATE, new EvidenceRef(earlier.seq(), match.originalStart, match.originalEnd), shadow));
                    }
                }
            }
        }
    }

    private SafetyAssessment toAssessment(List<Hit> hits, Demographics demographics) {
        Set<String> matched = new LinkedHashSet<>();
        Set<String> shadows = new LinkedHashSet<>();
        List<EvidenceRef> evidence = new ArrayList<>();
        boolean urgent = false;
        boolean prompt = false;
        boolean human = false;
        boolean unsuitable = false;
        boolean undetermined = false;
        String templateId = null;
        for (Hit hit : hits) {
            PolicyAction action = applyMissingContext(hit.rule, hit.action, demographics);
            if (hit.shadow) {
                if (action != PolicyAction.SUPPRESS) {
                    shadows.add(hit.rule.ruleId());
                    evidence.add(hit.evidence);
                }
                continue;
            }
            if (action == PolicyAction.SUPPRESS) {
                continue;
            }
            matched.add(hit.rule.ruleId());
            evidence.add(hit.evidence);
            if (action == PolicyAction.UNDETERMINED) {
                undetermined = true;
                if (templateId == null) {
                    templateId = SafetyCatalog.FALLBACK_DEFAULT;
                }
                continue;
            }
            switch (hit.rule.disposition()) {
                case URGENT -> {
                    urgent = true;
                    templateId = hit.rule.patientMessageTemplateId();
                }
                case HUMAN -> {
                    human = true;
                    if (templateId == null) {
                        templateId = hit.rule.patientMessageTemplateId();
                    }
                }
                case UNSUITABLE_FOR_ONLINE -> {
                    unsuitable = true;
                    if (templateId == null) {
                        templateId = hit.rule.patientMessageTemplateId();
                    }
                }
                case PROMPT_EVALUATION -> {
                    prompt = true;
                    if (templateId == null) {
                        templateId = hit.rule.patientMessageTemplateId();
                    }
                }
                case PROMPT_EVALUATION_BLOCK_ONLINE -> {
                    prompt = true;
                    unsuitable = true;
                    if (templateId == null) {
                        templateId = hit.rule.patientMessageTemplateId();
                    }
                }
                case NO_ESCALATION -> {
                }
            }
        }
        return new SafetyAssessment(
                urgent,
                prompt,
                human,
                unsuitable,
                undetermined,
                null,
                templateId,
                List.copyOf(matched),
                List.copyOf(shadows),
                List.copyOf(evidence),
                ModelStatus.DISABLED);
    }

    private PolicyAction applyMissingContext(SafetyRule rule, PolicyAction hitAction, Demographics demographics) {
        if (rule.requiredContext().isEmpty()) {
            return hitAction;
        }
        boolean missing = false;
        for (String required : rule.requiredContext()) {
            if ("age".equals(required) && demographics.age() == null) {
                missing = true;
            }
            if ("gender".equals(required) && demographics.gender() == null) {
                missing = true;
            }
        }
        if (!missing) {
            return hitAction;
        }
        return rule.missingContextPolicy();
    }

    private static Concatenation concatenate(List<ConsultationMessage> transcript) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < transcript.size(); i++) {
            if (i > 0) {
                text.append('\n');
            }
            text.append(transcript.get(i).text());
        }
        int seq = transcript.isEmpty() ? 1 : transcript.get(transcript.size() - 1).seq();
        return new Concatenation(text.toString(), seq);
    }

    private static List<Sentence> split(Normalized normalized) {
        String text = normalized.text;
        List<Sentence> sentences = new ArrayList<>();
        int cursor = 0;
        while (cursor < text.length()) {
            int next = text.length();
            int skip = 0;
            for (String splitter : SPLITTERS) {
                int found = text.indexOf(splitter, cursor);
                if (found >= 0 && found < next) {
                    next = found;
                    skip = splitter.length();
                }
            }
            if (next > cursor) {
                sentences.add(new Sentence(normalized, cursor, next));
            }
            cursor = Math.min(text.length(), next + skip);
        }
        if (sentences.isEmpty()) {
            sentences.add(new Sentence(normalized, 0, text.length()));
        }
        return sentences;
    }

    private static boolean occupied(boolean[] taken, int start, int end) {
        for (int i = start; i < end; i++) {
            if (taken[i]) {
                return true;
            }
        }
        return false;
    }

    private static void mark(boolean[] taken, int start, int end) {
        for (int i = start; i < end; i++) {
            taken[i] = true;
        }
    }

    private static String preceding(String text, int start, int chars) {
        int from = start;
        int counted = 0;
        while (from > 0 && counted < chars) {
            from--;
            counted++;
        }
        return text.substring(from, start);
    }

    private static boolean containsAny(String text, String[] needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private record Hit(SafetyRule rule, PolicyAction action, EvidenceRef evidence, boolean shadow) {
    }

    private record Match(int normalizedStart, int normalizedEnd, int originalStart, int originalEnd) {
    }

    private record Concatenation(String text, int joinedSeq) {
    }

    private static final class Normalized {
        private final String text;
        private final int[] originalIndexes;

        private Normalized(String text, int[] originalIndexes) {
            this.text = text;
            this.originalIndexes = originalIndexes;
        }

        static Normalized of(String original) {
            String nfkc = Normalizer.normalize(original == null ? "" : original, Normalizer.Form.NFKC);
            StringBuilder builder = new StringBuilder();
            List<Integer> indexes = new ArrayList<>();
            for (int i = 0; i < nfkc.length(); ) {
                int codePoint = nfkc.codePointAt(i);
                int next = i + Character.charCount(codePoint);
                if (Character.isWhitespace(codePoint)) {
                    i = next;
                    continue;
                }
                int mapped = Character.toLowerCase(codePoint);
                builder.appendCodePoint(mapped);
                indexes.add(i);
                i = next;
            }
            int[] map = new int[indexes.size()];
            for (int i = 0; i < indexes.size(); i++) {
                map[i] = indexes.get(i);
            }
            return new Normalized(builder.toString(), map);
        }

        int originalIndex(int normalizedIndex) {
            if (originalIndexes.length == 0) {
                return 0;
            }
            if (normalizedIndex < 0) {
                return originalIndexes[0];
            }
            if (normalizedIndex >= originalIndexes.length) {
                return originalIndexes[originalIndexes.length - 1];
            }
            return originalIndexes[normalizedIndex];
        }
    }

    private static final class Sentence {
        private final Normalized source;
        private final int start;
        private final int end;

        private Sentence(Normalized source, int start, int end) {
            this.source = source;
            this.start = start;
            this.end = end;
        }

        String normalized() {
            return source.text.substring(start, end);
        }

        int originalIndex(int normalizedOffsetInSentence) {
            return source.originalIndex(start + normalizedOffsetInSentence);
        }
    }
}
