package com.moonligh.consult.agent.domain.department;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AudienceSnapshotTest {

    @Test
    void childViewContainsOnlyChildAndUnrestrictedSpecialties() {
        DepartmentSnapshot child = DepartmentSeed.load().forAge(8);

        assertTrue(child.enabledSpecialtyNames().contains("小儿眼科"));
        assertTrue(child.enabledSpecialtyNames().contains("中医儿科"));
        assertTrue(child.enabledSpecialtyNames().contains("全科"));
        assertFalse(child.enabledSpecialtyNames().contains("胸外科"));
        assertFalse(child.phraseRefs().containsKey("胸外科"));
    }

    @Test
    void adultViewContainsOnlyAdultAndUnrestrictedSpecialties() {
        DepartmentSnapshot adult = DepartmentSeed.load().forAge(32);

        assertTrue(adult.enabledSpecialtyNames().contains("胸外科"));
        assertTrue(adult.enabledSpecialtyNames().contains("全科"));
        assertFalse(adult.enabledSpecialtyNames().contains("小儿眼科"));
        assertFalse(adult.enabledSpecialtyNames().contains("中医儿科"));
    }

    @Test
    void missingAgeUsesAdultView() {
        DepartmentSnapshot adult = DepartmentSeed.load().forAge(null);

        assertTrue(adult.enabledSpecialtyNames().contains("胸外科"));
        assertFalse(adult.enabledSpecialtyNames().contains("小儿眼科"));
    }
}
