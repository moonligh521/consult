package com.moonligh.consult.agent.domain.department;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void rejectsBlankOrIdentityTypo() {
        assertThrows(IllegalArgumentException.class, () -> rules.requireTypo("", "咳嗽"));
        assertThrows(IllegalArgumentException.class, () -> rules.requireTypo("头通", "头通"));
    }
}
