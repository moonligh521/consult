package com.moonligh.consult.agent.domain.triage;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.moonligh.consult.agent.domain.SpokenNormalizer;
import com.moonligh.consult.agent.domain.department.CanonSynonym;
import com.moonligh.consult.agent.domain.department.DepartmentCatalog;
import com.moonligh.consult.agent.domain.department.DepartmentSnapshot;
import com.moonligh.consult.agent.domain.department.SpecialtyRef;
import com.moonligh.consult.agent.domain.department.SynonymStatus;

/**
 * N4 主诉归约的规则层：病名字面、已审同义规则、症状命中，按固定顺序给出能否自己定。
 * 定不了（只剩歧义症状、或有话但目录全不中）才交模型。
 */
public class ComplaintCanon {

    public static final String EMPTY = "EMPTY";
    public static final String UNCERTAIN_MARKER = "UNCERTAIN_MARKER";
    public static final String DISEASE_LITERAL = "DISEASE_LITERAL";
    public static final String SYNONYM = "SYNONYM";
    public static final String SYMPTOM_ONLY = "SYMPTOM_ONLY";
    public static final String AMBIGUOUS_ONLY = "AMBIGUOUS_ONLY";
    public static final String NO_HIT = "NO_HIT";
    public static final String TOO_SHORT = "TOO_SHORT";

    /** 全不中时，紧凑串至少这么长才算「说了具体内容」，交模型。 */
    static final int MIN_MODEL_LENGTH = 6;
    static final int MAX_TERMS = 2;

    private static final Pattern HISTORY_BEFORE = Pattern.compile("(既往|以前|曾经|之前|老毛病|一直有)[^，。；,;.!?]{0,6}$");
    private static final Pattern HISTORY_AFTER = Pattern.compile("^[^，。；,;.!?]{0,3}(病史|多年|好几年|十几年|吃药控制)");

    private final DepartmentCatalog catalog;

    public ComplaintCanon(DepartmentCatalog catalog) {
        this.catalog = catalog;
    }

    public CanonResult rule(String description) {
        return rule(description, catalog.snapshot());
    }

    public CanonResult rule(String description, DepartmentSnapshot snapshot) {
        String text = SpokenNormalizer.forMatch(description, snapshot);
        if (text.isEmpty()) {
            return result(List.of(), List.of(), List.of(), List.of(), Certainty.CERTAIN, EMPTY);
        }
        if (SpokenNormalizer.uncertainDepartment(description, snapshot)) {
            return result(List.of(), List.of(), List.of(), List.of(), Certainty.CERTAIN, UNCERTAIN_MARKER);
        }
        List<CanonTerm> current = new ArrayList<>();
        List<CanonTerm> history = new ArrayList<>();
        List<SymptomHit> symptoms = new ArrayList<>();
        scan(text, snapshot, current, history, symptoms);
        List<String> shadows = new ArrayList<>();
        boolean literal = !current.isEmpty();
        boolean synonym = false;
        for (CanonSynonym rule : snapshot.synonyms()) {
            CanonSynonym.Hit hit = rule.match(text);
            if (hit == null) {
                continue;
            }
            Span span = new Span(hit.start(), hit.end());
            if (rule.status() == SynonymStatus.REVIEWED) {
                CanonTerm term = new CanonTerm(rule.targetTerm(), rule.ruleId(), span);
                if (inHistory(text, span)) {
                    addTerm(history, term);
                } else if (addTerm(current, term)) {
                    synonym = true;
                }
            } else if (rule.status() == SynonymStatus.DRAFT) {
                shadows.add(rule.ruleId());
            }
        }
        current.sort((left, right) -> Integer.compare(left.evidence().start(), right.evidence().start()));
        List<CanonTerm> terms = current.size() > MAX_TERMS ? current.subList(0, MAX_TERMS) : current;
        if (!terms.isEmpty()) {
            return result(terms, history, symptoms, shadows, Certainty.CERTAIN, literal ? DISEASE_LITERAL : SYNONYM);
        }
        boolean clear = false;
        boolean ambiguous = false;
        for (SymptomHit hit : symptoms) {
            if (hit.ambiguous()) {
                ambiguous = true;
            } else if (!hit.refs().isEmpty()) {
                clear = true;
            }
        }
        if (clear) {
            return result(terms, history, symptoms, shadows, Certainty.CERTAIN, SYMPTOM_ONLY);
        }
        if (ambiguous) {
            return result(terms, history, symptoms, shadows, Certainty.UNCERTAIN, AMBIGUOUS_ONLY);
        }
        // 拼音/拉丁字母可能是短主诉（kesou、头tong），仍交模型，不因字数不够直接全科
        if (text.length() >= MIN_MODEL_LENGTH || hasLatinLetter(text)) {
            return result(terms, history, symptoms, shadows, Certainty.UNCERTAIN, NO_HIT);
        }
        return result(terms, history, symptoms, shadows, Certainty.CERTAIN, TOO_SHORT);
    }

    /** 紧凑串里是否含拉丁字母。用于放行拼音短句进模型。 */
    static boolean hasLatinLetter(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); ) {
            int code = text.codePointAt(i);
            if ((code >= 'a' && code <= 'z') || (code >= 'A' && code <= 'Z')) {
                return true;
            }
            i += Character.charCount(code);
        }
        return false;
    }

    /** 从左到右最长匹配、不重叠：病名、目录症状、歧义症状一起扫。 */
    private static void scan(
            String text,
            DepartmentSnapshot snapshot,
            List<CanonTerm> current,
            List<CanonTerm> history,
            List<SymptomHit> symptoms) {
        Set<String> phrases = new LinkedHashSet<>(snapshot.diseaseIndex().keySet());
        phrases.addAll(snapshot.symptomPhrases());
        phrases.addAll(snapshot.ambiguousSymptoms());
        Set<String> seen = new LinkedHashSet<>();
        int index = 0;
        while (index < text.length()) {
            String match = longestAt(text, index, phrases);
            if (match == null) {
                index++;
                continue;
            }
            Span span = new Span(index, index + match.length());
            if (snapshot.isDisease(match)) {
                CanonTerm term = new CanonTerm(match, CanonTerm.LITERAL, span);
                if (inHistory(text, span)) {
                    addTerm(history, term);
                } else {
                    addTerm(current, term);
                }
            } else if (seen.add(match)) {
                List<SpecialtyRef> refs = snapshot.phraseRefs().getOrDefault(match, List.of());
                symptoms.add(new SymptomHit(match, snapshot.isAmbiguous(match), refs, span));
            }
            index = span.end();
        }
    }

    private static boolean inHistory(String text, Span span) {
        String before = text.substring(0, span.start());
        String after = text.substring(span.end());
        return HISTORY_BEFORE.matcher(before).find() || HISTORY_AFTER.matcher(after).find();
    }

    private static boolean addTerm(List<CanonTerm> list, CanonTerm term) {
        for (CanonTerm existing : list) {
            if (existing.term().equals(term.term())) {
                return false;
            }
        }
        list.add(term);
        return true;
    }

    private static String longestAt(String text, int index, Set<String> phrases) {
        String best = null;
        for (String phrase : phrases) {
            if (phrase.isEmpty() || !text.startsWith(phrase, index)) {
                continue;
            }
            if (best == null || phrase.length() > best.length()) {
                best = phrase;
            }
        }
        return best;
    }

    private static CanonResult result(
            List<CanonTerm> terms,
            List<CanonTerm> history,
            List<SymptomHit> symptoms,
            List<String> shadows,
            Certainty certainty,
            String reason) {
        boolean resolved = certainty == Certainty.CERTAIN;
        return new CanonResult(terms, history, symptoms, shadows, certainty, DecisionSource.RULE, reason, resolved);
    }
}
