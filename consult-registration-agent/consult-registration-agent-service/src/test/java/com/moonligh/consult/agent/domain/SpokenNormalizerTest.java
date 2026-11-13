package com.moonligh.consult.agent.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class SpokenNormalizerTest {

    @Test
    void mapsRegistrationTyposAfterCompactingSpaces() {
        assertEquals("头痛两天", SpokenNormalizer.forMatch("头 通两天"));
        assertEquals("咳嗽三天", SpokenNormalizer.forMatch("刻嗽三天"));
        assertEquals("腹泻", SpokenNormalizer.forMatch("腹泄"));
        assertEquals("呼息困难", SpokenNormalizer.forMatch("呼息困难"));
        assertEquals("胸疼", SpokenNormalizer.forMatch("胸疼"));
    }

    @Test
    void keepsOriginalWhenNoTypo() {
        assertEquals("干咳半年", SpokenNormalizer.forMatch("干咳半年"));
    }

    @Test
    void traditionalChineseMapsToSimplifiedForRouting() {
        assertEquals("头痛两天", SpokenNormalizer.forMatch("頭痛两天"));
        assertEquals("腹泻三天", SpokenNormalizer.forMatch("腹瀉三天"));
    }

    @Test
    void doesNotChangeSafetyCompactForDraftChestPainTypo() {
        assertEquals("胸疼", CompactText.of("胸疼"));
        assertFalse(CompactText.of("胸疼").contains("胸痛"));
    }
}
