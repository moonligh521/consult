package com.moonligh.consult.agent.domain.department;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.moonligh.consult.agent.domain.safety.CatalogLoader;
import com.moonligh.consult.agent.domain.safety.DraftMode;
import com.moonligh.consult.agent.domain.safety.SafetyCatalog;

class LexiconWriteRulesTest {

    private final SafetyCatalog catalog = CatalogLoader.loadDefault(DraftMode.SHADOW);
    private final LexiconWriteRules rules = new LexiconWriteRules(catalog);

    @Test
    void compactAndKeepWhitelistedOneCharKeyword() {
        assertEquals("咳", rules.requireKeyword(" 咳 "));
        assertEquals("喘", rules.requireKeyword("喘"));
        assertEquals("足跟疼", rules.requireKeyword("足 跟 疼"));
    }

    @Test
    void rejectsOtherOneCharKeywords() {
        assertThrows(IllegalArgumentException.class, () -> rules.requireKeyword("疼"));
    }

    @Test
    void rejectsDraftAliasTypo() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> rules.requireTypo("胸疼", "胸痛"));
        assertTrue(error.getMessage().contains("DRAFT"));
        assertThrows(IllegalArgumentException.class, () -> rules.requireTypo("呼息困难", "呼吸困难"));
        assertThrows(IllegalArgumentException.class, () -> rules.requireTypo("口唇发子", "口唇发紫"));
    }

    @Test
    void acceptsRegistrationTypo() {
        MatchTypo typo = rules.requireTypo("刻 嗽", "咳嗽");
        assertEquals("刻嗽", typo.from());
        assertEquals("咳嗽", typo.to());
    }

    @Test
    void synonymTargetMustBeCatalogDisease() {
        DepartmentSnapshot snapshot = MemoryDepartmentCatalog.seed().snapshot();
        CanonSynonym symptomTarget = new CanonSynonym("SYN-X", "头晕", List.of(List.of("天旋地转")), List.of(), null,
                SynonymStatus.REVIEWED);
        assertThrows(IllegalArgumentException.class, () -> rules.requireSynonym(symptomTarget, snapshot));
        CanonSynonym ok = new CanonSynonym("SYN-Y", "高血压", List.of(List.of("血压高")), List.of(), null,
                SynonymStatus.REVIEWED);
        assertEquals(SynonymStatus.DRAFT, rules.requireSynonym(ok, snapshot).status());
    }

    @Test
    void synonymPatternIsWhitelisted() {
        DepartmentSnapshot snapshot = MemoryDepartmentCatalog.seed().snapshot();
        CanonSynonym numeric = new CanonSynonym("SYN-P", "高血压", List.of(List.of("血压")), List.of(),
                "(血压|高压)(1[4-9]\\d|2\\d\\d)", SynonymStatus.DRAFT);
        assertEquals("SYN-P", rules.requireSynonym(numeric, snapshot).ruleId());
        CanonSynonym free = new CanonSynonym("SYN-Q", "高血压", List.of(List.of("血压")), List.of(), ".*头.*",
                SynonymStatus.DRAFT);
        assertThrows(IllegalArgumentException.class, () -> rules.requireSynonym(free, snapshot));
    }

    @Test
    void reviewNeedsReviewer() {
        assertThrows(IllegalArgumentException.class, () -> rules.requireReviewer(" "));
    }

    @Test
    void rejectsBlankOrIdentityTypo() {
        assertThrows(IllegalArgumentException.class, () -> rules.requireTypo("", "咳嗽"));
        assertThrows(IllegalArgumentException.class, () -> rules.requireTypo("头通", "头通"));
    }
}
