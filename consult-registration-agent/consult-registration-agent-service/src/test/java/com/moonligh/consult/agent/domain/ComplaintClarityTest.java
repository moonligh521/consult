package com.moonligh.consult.agent.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ComplaintClarityTest {

    private final ComplaintClarity clarity = new ComplaintClarity(new SymptomExtractor(), new SlotParser());

    @Test
    void bloatingIsSpecificEnough() {
        assertFalse(clarity.isVague("胃胀"));
        assertFalse(clarity.isVague("胃 胀"));
    }

    @Test
    void vomitingTwiceIsSpecificEnough() {
        assertFalse(clarity.isVague("我呕吐两次"));
    }

    @Test
    void insomniaAndChestTightnessAreSymptoms() {
        assertFalse(clarity.isVague("失眠"));
        assertFalse(clarity.isVague("胸闷"));
    }

    @Test
    void toothacheIsSpecificEnoughAndUnwellStillAsk() {
        assertFalse(clarity.isVague("牙疼"));
        assertTrue(clarity.isVague("有点不舒服"));
    }

    @Test
    void heelPainAndParonychiaAreSpecificEnough() {
        assertFalse(clarity.isVague("足跟疼"));
        assertFalse(clarity.isVague("甲沟炎"));
    }

    @Test
    void spokenTyposAreSpecificEnough() {
        assertFalse(clarity.isVague("头通"));
        assertFalse(clarity.isVague("刻嗽"));
    }
}
