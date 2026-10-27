package com.moonligh.consult.agent.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class SlotParserTest {

    private final SlotParser parser = new SlotParser();

    @Test
    void chineseDurationVariants() {
        assertEquals("好几天", parser.duration("咳嗽好几天"));
        assertEquals("半个多月", parser.duration("头疼半个多月"));
        assertEquals("一个多月", parser.duration("腹泻一个多月"));
        assertEquals("两三天", parser.duration("腹痛两三天"));
        assertEquals("3天", parser.duration("咳嗽3天"));
    }

    @Test
    void numericEnglishDaysOnly() {
        assertEquals("3 days", parser.duration("cough 3 days"));
        assertNull(parser.duration("cough three days"));
    }

    @Test
    void vagueDurationStaysMissing() {
        assertNull(parser.duration("咳嗽记不清多久了"));
    }
}
