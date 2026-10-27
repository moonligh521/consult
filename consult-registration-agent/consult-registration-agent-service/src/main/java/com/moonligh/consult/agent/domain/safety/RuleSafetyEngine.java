package com.moonligh.consult.agent.domain.safety;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.moonligh.consult.framework.model.ModelStatus;

/**
 * 按急症目录匹配患者原话。
 * 步骤：归一 → 分句 → 找别名 → 看否定/时间 → 按规则写出处置。
 * 模型不能调用本类去设置最终状态。
 */
public class RuleSafetyEngine {

    /**
     * 规则引擎版本，写入审计表。
     */
    public static final String VERSION = "1.0.0";

    /**
     * 用来把长句切开，避免前后半句互相干扰。
     */
    private static final String[] SPLITTERS = {"但是", "不过", "可是", "然而", "，", "。", "；", "！", "？", ",", ".", ";", "!", "?", "\n"};
    /**
     * 双重否定，当成不确定。
     */
    private static final String[] DOUBLE_NEGATIONS = {"不是没有", "没有不", "并非没有"};
    /**
     * 患者自己也不确定。
     */
    private static final String[] UNCERTAIN_WORDS = {"是不是", "会不会", "好像", "可能", "不确定", "说不清"};
    /**
     * 否定词。只看别名前面几字，避免后半句把前面的急症否掉。
     */
    private static final String[] NEGATIONS = {"没有", "并非", "不是", "否认", "没", "无", "不", "未"};
    /**
     * 别名后面的否定，只认「不是/并非」，避免「转人工不好用」被「不」误伤。
     */
    private static final String[] TRAILING_NEGATIONS = {"不是", "并非"};
    /**
     * 已经缓解的说法。
     */
    private static final String[] RESOLVED_WORDS = {"已经好了", "缓解了", "不疼了", "消失了", "现在没事"};
    /**
     * 后面这句把前面说过的症状又拉回当前。
     */
    private static final String[] CURRENT_ANAPHORA = {"现在开始痛了", "现在开始疼了", "又痛了", "又疼了"};

    /**
     * 评估全部原话，返回急症、转人工等结论。
     */
    public SafetyAssessment evaluate(
            List<ConsultationMessage> transcript,
            Demographics demographics,
            SafetyCatalog catalog,
            boolean includeDraftInDecision) {
        List<Hit> hits = new ArrayList<>();
        // 先逐条匹配。
        for (ConsultationMessage message : transcript) {
            collectHits(message.seq(), message.text(), catalog, includeDraftInDecision, hits);
        }
        // 再把全部原话拼起来扫一遍，避免症状和病程拆在两句里漏掉。
        Concatenation concatenation = concatenate(transcript);
        collectHits(concatenation.joinedSeq, concatenation.text, catalog, includeDraftInDecision, hits);
        // 后一句「又痛了」要把前面说过的症状拉回来。
        applyAnaphora(transcript, catalog, includeDraftInDecision, hits);
        return toAssessment(hits, demographics);
    }

    /**
     * 在一段文字里找规则别名，命中就记一条 Hit。
     */
    private void collectHits(
            int evidenceSeq,
            String original,
            SafetyCatalog catalog,
            boolean includeDraftInDecision,
            List<Hit> hits) {
        Normalized normalized = Normalized.of(original);
        List<Sentence> sentences = split(normalized);
        for (SafetyRule rule : catalog.rulesForEngine()) {
            // DRAFT 规则默认只记影子，不参与患者决策。
            if (rule.status() == RuleStatus.DRAFT && !includeDraftInDecision) {
                collectDraftShadows(rule, sentences, evidenceSeq, hits);
                continue;
            }
            for (Sentence sentence : sentences) {
                hits.addAll(hitsInSentence(rule, sentence, evidenceSeq, false));
            }
        }
    }

    /**
     * DRAFT 命中只做影子记录。
     */
    private void collectDraftShadows(SafetyRule rule, List<Sentence> sentences, int evidenceSeq, List<Hit> hits) {
        for (Sentence sentence : sentences) {
            hits.addAll(hitsInSentence(rule, sentence, evidenceSeq, true));
        }
    }

    /**
     * 一句里所有别名命中。每个命中带上否定/不确定后的处置。
     */
    private List<Hit> hitsInSentence(SafetyRule rule, Sentence sentence, int evidenceSeq, boolean shadow) {
        List<Match> matches = aliasMatches(rule, sentence);
        List<Hit> hits = new ArrayList<>();
        for (Match match : matches) {
            PolicyAction action = actionOf(rule, sentence, match);
            hits.add(new Hit(rule, action, new EvidenceRef(evidenceSeq, match.originalStart, match.originalEnd), shadow));
        }
        return hits;
    }

    /**
     * 在一句里找别名。长别名优先，同一段字只算一次。
     */
    private List<Match> aliasMatches(SafetyRule rule, Sentence sentence) {
        List<Match> matches = new ArrayList<>();
        String sentenceText = sentence.normalized();
        boolean[] taken = new boolean[sentenceText.length()];
        List<String> aliases = new ArrayList<>(rule.aliases());
        // 先匹配长的，避免「胸痛」抢走「持续性胸痛」的位置。
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

    /**
     * 看这句是不确定、否定、已缓解，还是当前阳性。
     */
    private PolicyAction actionOf(SafetyRule rule, Sentence sentence, Match match) {
        String sentenceText = sentence.normalized();
        // 否定只看别名前面 6 个字，避免「没有发烧，但胸痛」把胸痛否掉。
        String window = preceding(sentenceText, match.normalizedStart, 6);
        String after = following(sentenceText, match.normalizedEnd, 6);
        boolean unclear = containsAny(sentenceText, DOUBLE_NEGATIONS) || containsAny(sentenceText, UNCERTAIN_WORDS);
        boolean negated = containsAny(window, NEGATIONS) || containsAny(after, TRAILING_NEGATIONS);
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

    /**
     * 后一句说「又痛了」，把前面原话里的症状按当前阳性再记一次。
     */
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

    /**
     * 把命中汇总成一次评估结果。影子命中不改患者状态。
     */
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

    /**
     * 规则要求年龄或性别，但还没抽到时，改走缺上下文策略。
     */
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

    /**
     * 把全部原话用换行拼成一段，证据序号用最后一句。
     */
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

    /**
     * 按逗号、句号、「但是」等把文字切开。
     */
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

    /**
     * 这段位置是不是已经被更长的别名占过。
     */
    private static boolean occupied(boolean[] taken, int start, int end) {
        for (int i = start; i < end; i++) {
            if (taken[i]) {
                return true;
            }
        }
        return false;
    }

    /**
     * 把这段位置标成已占用。
     */
    private static void mark(boolean[] taken, int start, int end) {
        for (int i = start; i < end; i++) {
            taken[i] = true;
        }
    }

    /**
     * 取出别名前面若干个字，用来看有没有否定。
     */
    private static String preceding(String text, int start, int chars) {
        int from = start;
        int counted = 0;
        while (from > 0 && counted < chars) {
            from--;
            counted++;
        }
        return text.substring(from, start);
    }

    /**
     * 取出别名后面若干个字，用来看有没有「不是转人」这类后置否定。
     */
    private static String following(String text, int start, int chars) {
        int to = Math.min(text.length(), start + chars);
        return text.substring(start, to);
    }

    /**
     * 文字里有没有出现这组词中的任何一个。
     */
    private static boolean containsAny(String text, String[] needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 一次别名命中：哪条规则、怎么处置、证据在哪、是不是影子。
     */
    private record Hit(SafetyRule rule, PolicyAction action, EvidenceRef evidence, boolean shadow) {
    }

    /**
     * 别名在归一化文字和原始文字里的起止位置。
     */
    private record Match(int normalizedStart, int normalizedEnd, int originalStart, int originalEnd) {
    }

    /**
     * 拼起来的全部原话。
     */
    private record Concatenation(String text, int joinedSeq) {
    }

    /**
     * 去掉空白、转小写后的文字，并记下每个字对应原文位置。
     */
    private static final class Normalized {
        private final String text;
        private final int[] originalIndexes;

        private Normalized(String text, int[] originalIndexes) {
            this.text = text;
            this.originalIndexes = originalIndexes;
        }

        /**
         * 把原话归一化，方便匹配别名。
         */
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

    /**
     * 切开后的一句，仍能映射回原文位置。
     */
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
