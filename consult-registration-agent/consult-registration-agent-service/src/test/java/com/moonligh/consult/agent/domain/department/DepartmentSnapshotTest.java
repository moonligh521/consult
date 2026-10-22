package com.moonligh.consult.agent.domain.department;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.moonligh.consult.agent.api.enums.Department;
import com.moonligh.consult.agent.domain.SpokenNormalizer;

class DepartmentSnapshotTest {

    @Test
    void seedFallbackIsGeneral() {
        DepartmentSnapshot snapshot = MemoryDepartmentCatalog.seed().snapshot();
        assertEquals("全科", snapshot.fallback().name());
        assertEquals(Department.GENERAL, snapshot.fallbackDepartment());
    }

    @Test
    void fallbackNameMustBeGeneral() {
        DepartmentRecord internalAsFallback = new DepartmentRecord(
                1, "内科", true, 1, true, 0, null, List.of(), List.of());
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new DepartmentSnapshot("x", List.of(internalAsFallback)));
        assertTrue(error.getMessage().contains("全科"));
    }

    @Test
    void seedSnapshotCarriesTyposAndUncertainMarkers() {
        DepartmentSnapshot snapshot = MemoryDepartmentCatalog.seed().snapshot();
        assertEquals("咳嗽三天", SpokenNormalizer.forMatch("刻嗽三天", snapshot));
        assertEquals("头痛两天", SpokenNormalizer.forMatch("头 通两天", snapshot));
        assertTrue(SpokenNormalizer.uncertainDepartment("不知道挂哪个科", snapshot));
        assertFalse(SpokenNormalizer.uncertainDepartment("咳嗽还头痛", snapshot));
    }

    @Test
    void withoutEnabledKeepsLexicon() {
        DepartmentSnapshot snapshot = MemoryDepartmentCatalog.seed()
                .withoutEnabled(Department.ORTHOPEDICS.getCode())
                .snapshot();
        assertEquals("头痛", SpokenNormalizer.forMatch("头通", snapshot));
        assertTrue(SpokenNormalizer.uncertainDepartment("说不清", snapshot));
    }

    @Test
    void symptomPhrasesAreRoutingPlusSafetyWordsNotOverride() {
        List<String> phrases = MemoryDepartmentCatalog.seed().snapshot().symptomPhrases();
        assertTrue(phrases.contains("咳嗽"));
        assertTrue(phrases.contains("足跟疼"));
        assertTrue(phrases.contains("皮疹"));
        assertTrue(phrases.contains("呼吸困难"));
        assertTrue(phrases.contains("胸痛"));
        assertFalse(phrases.contains("看中医"));
        assertFalse(phrases.contains("孩子"));
    }
}
