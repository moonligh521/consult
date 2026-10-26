package com.moonligh.consult.agent.domain.safety;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.moonligh.consult.agent.domain.CompactText;

class ModelTriggerScanTest {

    private final ModelTriggerScan scan = ModelTriggerScan.load();

    private boolean hits(String text, String id) {
        return scan.scanText(CompactText.of(text)).contains(id);
    }

    @Test
    void chestNeedsLocationAndQuality() {
        assertTrue(hits("心口像压了块石头", "T-CHEST"));
        assertTrue(!hits("胸前起了个疹子", "T-CHEST"));
    }

    @Test
    void breathSurvivesSpacing() {
        assertTrue(hits("喘 不上 气", "T-BREATH"));
    }

    @Test
    void ordinaryComplaintHasNoTrigger() {
        assertTrue(scan.scanText(CompactText.of("干咳半年，运动后加重")).isEmpty());
        assertTrue(scan.scanText(CompactText.of("胃胀，吃不下饭，两周")).isEmpty());
    }

    @Test
    void childFeverAndPregnancyBleeding() {
        assertTrue(hits("宝宝高烧不退", "T-CHILD"));
        assertTrue(hits("怀孕两个月有点出血", "T-PREG"));
        assertTrue(!hits("宝宝有点咳嗽", "T-CHILD"));
    }
}
