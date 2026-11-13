package com.moonligh.consult.agent.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class SymptomExtractorTest {

    private final SymptomExtractor extractor = new SymptomExtractor();

    @Test
    void extractsRoutingKeyword() {
        assertEquals(List.of("胃胀"), extractor.extract("胃胀"));
        assertEquals(List.of("足跟疼"), extractor.extract("足跟疼"));
    }

    @Test
    void keepsSafetyPhrasesEvenIfNotRouting() {
        assertEquals(List.of("呼吸困难"), extractor.extract("呼吸困难"));
        assertEquals(List.of("胸痛"), extractor.extract("胸痛"));
    }

    @Test
    void doesNotExtractOverrideMarkersAsSymptoms() {
        assertTrue(extractor.extract("看中医").isEmpty());
        assertTrue(extractor.extract("孩子").isEmpty());
    }

    @Test
    void spokenTypoBecomesCatalogSymptom() {
        assertEquals(List.of("头痛"), extractor.extract("头通"));
        assertEquals(List.of("咳嗽"), extractor.extract("刻嗽"));
    }
}
