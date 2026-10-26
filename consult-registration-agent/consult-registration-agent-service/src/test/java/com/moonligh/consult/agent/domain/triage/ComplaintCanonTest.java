package com.moonligh.consult.agent.domain.triage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.moonligh.consult.agent.domain.department.CanonSynonym;
import com.moonligh.consult.agent.domain.department.DepartmentSnapshot;
import com.moonligh.consult.agent.domain.department.MemoryDepartmentCatalog;
import com.moonligh.consult.agent.domain.department.SynonymStatus;

class ComplaintCanonTest {

    private final DepartmentSnapshot seed = MemoryDepartmentCatalog.seed().snapshot();
    private final ComplaintCanon canon = new ComplaintCanon(MemoryDepartmentCatalog.seed());

    /** 把指定同义规则改成已审，其余保持种子状态。 */
    static DepartmentSnapshot reviewed(DepartmentSnapshot snapshot, String... ruleIds) {
        List<CanonSynonym> rules = new ArrayList<>();
        for (CanonSynonym rule : snapshot.synonyms()) {
            boolean promote = List.of(ruleIds).contains(rule.ruleId());
            rules.add(promote
                    ? new CanonSynonym(rule.ruleId(), rule.targetTerm(), rule.allGroups(), rule.noneTerms(),
                            rule.pattern(), SynonymStatus.REVIEWED)
                    : rule);
        }
        return snapshot.withSynonyms(rules);
    }

    @Test
    void diseaseLiteralIsCertain() {
        CanonResult result = canon.rule("高血压，头晕三天", seed);
        assertEquals(ComplaintCanon.DISEASE_LITERAL, result.reason());
        assertEquals(List.of("高血压"), result.termNames());
        assertTrue(result.certain());
        assertTrue(result.terms().get(0).evidence() != null);
    }

    @Test
    void historyTermIsNotCurrent() {
        CanonResult after = canon.rule("我有高血压好几年了，这两天咳嗽", seed);
        assertTrue(after.terms().isEmpty());
        assertEquals("高血压", after.historyTerms().get(0).term());
        assertEquals(ComplaintCanon.SYMPTOM_ONLY, after.reason());

        CanonResult before = canon.rule("以前得过胃炎，现在咳嗽", seed);
        assertTrue(before.terms().isEmpty());
        assertEquals("胃炎", before.historyTerms().get(0).term());
    }

    @Test
    void clearSymptomIsCertainWithoutTerms() {
        CanonResult result = canon.rule("咳嗽三天", seed);
        assertEquals(ComplaintCanon.SYMPTOM_ONLY, result.reason());
        assertTrue(result.certain());
        assertTrue(result.terms().isEmpty());
    }

    @Test
    void ambiguousOnlyNeedsModel() {
        CanonResult result = canon.rule("头晕乏力一周", seed);
        assertEquals(ComplaintCanon.AMBIGUOUS_ONLY, result.reason());
        assertFalse(result.certain());
    }

    @Test
    void draftSynonymOnlyShadows() {
        CanonResult result = canon.rule("量血压160/100，头胀两天", seed);
        assertTrue(result.terms().isEmpty());
        assertTrue(result.shadowRuleIds().contains("SYN-HTN-002"));
    }

    @Test
    void reviewedSynonymInHistoryIsNotCurrent() {
        DepartmentSnapshot snapshot = reviewed(seed, "SYN-DM-002");
        CanonResult result = canon.rule("以前口渴喝水多夜里老跑厕所，这两天咳嗽", snapshot);
        assertTrue(result.termNames().isEmpty(), "既往同义不应当成这次要看的病");
        assertEquals(List.of("糖尿病"), result.historyTerms().stream().map(CanonTerm::term).toList());
        assertEquals(ComplaintCanon.SYMPTOM_ONLY, result.reason());
    }

    @Test
    void reviewedSynonymAddsTerm() {
        DepartmentSnapshot snapshot = reviewed(seed, "SYN-HTN-002", "SYN-DM-002");
        CanonResult pressure = canon.rule("量血压160/100，头胀两天", snapshot);
        assertEquals(ComplaintCanon.SYNONYM, pressure.reason());
        assertEquals(List.of("高血压"), pressure.termNames());

        CanonResult thirst = canon.rule("最近老是口渴，喝水多，夜里老跑厕所", snapshot);
        assertEquals(List.of("糖尿病"), thirst.termNames());
    }

    @Test
    void synonymNoneTermBlocks() {
        CanonSynonym rule = new CanonSynonym("SYN-T", "高血压", List.of(List.of("血压高")), List.of("不"), null,
                SynonymStatus.REVIEWED);
        DepartmentSnapshot snapshot = seed.withSynonyms(List.of(rule));
        assertTrue(canon.rule("血压高不高不清楚，头胀", snapshot).terms().isEmpty());
        assertEquals(List.of("高血压"), canon.rule("血压高，头胀", snapshot).termNames());
    }

    @Test
    void atMostTwoTerms() {
        CanonResult result = canon.rule("高血压，糖尿病，痛风都有点", seed);
        assertEquals(2, result.terms().size());
        assertEquals(List.of("高血压", "糖尿病"), result.termNames());
    }

    @Test
    void uncertainMarkerAndShortTextAreCertain() {
        assertEquals(ComplaintCanon.UNCERTAIN_MARKER, canon.rule("不知道挂哪个科，头晕", seed).reason());
        assertEquals(ComplaintCanon.TOO_SHORT, canon.rule("看看", seed).reason());
        assertEquals(ComplaintCanon.EMPTY, canon.rule("  ", seed).reason());
    }

    @Test
    void shortPinyinNeedsModel() {
        CanonResult kesou = canon.rule("kesou", seed);
        assertEquals(ComplaintCanon.NO_HIT, kesou.reason());
        assertFalse(kesou.certain());

        CanonResult mixed = canon.rule("头tong", seed);
        assertEquals(ComplaintCanon.NO_HIT, mixed.reason());
        assertFalse(mixed.certain());
    }

    @Test
    void longMissNeedsModel() {
        CanonResult result = canon.rule("体检报告看不懂", seed);
        assertEquals(ComplaintCanon.NO_HIT, result.reason());
        assertFalse(result.certain());
    }
}
