package com.moonligh.consult.identity.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

class DoctorGradeTest {

    @Test
    void titlesMapToPackages() {
        assertEquals(1, DoctorGrade.ofTitle("住院医师"));
        assertEquals(2, DoctorGrade.ofTitle("主治医师"));
        assertEquals(3, DoctorGrade.ofTitle("副主任医师"));
        assertEquals(4, DoctorGrade.ofTitle("主任医师"));
        assertEquals(2, DoctorGrade.ofTitle(null));
    }

    @Test
    void queueUsesPurchasedGradeOnly() {
        assertEquals(List.of(2), DoctorGrade.searchOrder(2, false));
        assertEquals(List.of(3), DoctorGrade.searchOrder(3, false));
        assertNull(DoctorGrade.searchOrder(null, false));
    }

    @Test
    void upgradeAddsOneGrade() {
        assertEquals(List.of(2, 3), DoctorGrade.searchOrder(2, true));
        assertEquals(List.of(3, 4), DoctorGrade.searchOrder(3, true));
        assertEquals(List.of(4), DoctorGrade.searchOrder(4, true));
        assertEquals(List.of(1, 2), DoctorGrade.searchOrder(1, true));
    }
}
