package com.moonligh.consult.agent.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class MatchQueueTest {

    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);
    private static final Instant T0 = Instant.parse("2026-10-27T11:00:00Z");

    @Test
    void firstPayerIsNotBlocked() {
        MatchQueue.Entry first = entry("c1", "o1", T0, 3);
        MatchQueue.Entry second = entry("c2", "o2", T0.plusSeconds(10), 3);
        assertFalse(MatchQueue.blockedByEarlier(first, List.of(first, second), T0.plusSeconds(5), ONE_MINUTE));
        assertTrue(MatchQueue.blockedByEarlier(second, List.of(first, second), T0.plusSeconds(5), ONE_MINUTE));
    }

    @Test
    void laterPayerOfSameGradeWaits() {
        MatchQueue.Entry first = entry("c1", "o1", T0, 2);
        MatchQueue.Entry tenth = entry("c10", "o10", T0.plusSeconds(90), 2);
        assertTrue(MatchQueue.blockedByEarlier(tenth, List.of(first, tenth), T0.plusSeconds(20), ONE_MINUTE));
    }

    @Test
    void differentGradeCanSkipIfNotCompeting() {
        MatchQueue.Entry expert = entry("c1", "o1", T0, 4);
        MatchQueue.Entry fast = entry("c2", "o2", T0.plusSeconds(5), 2);
        assertFalse(MatchQueue.blockedByEarlier(fast, List.of(expert, fast), T0.plusSeconds(10), ONE_MINUTE));
    }

    @Test
    void upgradeAfterOneMinuteCompetesWithHigherGrade() {
        MatchQueue.Entry tertiary = entry("c1", "o1", T0, 3);
        MatchQueue.Entry expert = entry("c2", "o2", T0.plusSeconds(10), 4);
        assertFalse(MatchQueue.blockedByEarlier(expert, List.of(tertiary, expert), T0.plusSeconds(30), ONE_MINUTE));
        assertTrue(MatchQueue.blockedByEarlier(expert, List.of(tertiary, expert), T0.plusSeconds(70), ONE_MINUTE));
    }

    @Test
    void otherSpecialtyDoesNotBlock() {
        MatchQueue.Entry cough = new MatchQueue.Entry("c1", "o1", T0, 3, "呼吸内科");
        MatchQueue.Entry skin = new MatchQueue.Entry("c2", "o2", T0.plusSeconds(1), 3, "皮肤科");
        assertFalse(MatchQueue.blockedByEarlier(skin, List.of(cough, skin), T0.plusSeconds(5), ONE_MINUTE));
    }

    @Test
    void upgradeAddsExactlyOneGrade() {
        assertEquals(List.of(3), MatchQueue.grades(3, false));
        assertEquals(List.of(3, 4), MatchQueue.grades(3, true));
        assertEquals(List.of(4), MatchQueue.grades(4, true));
    }

    private static MatchQueue.Entry entry(String consult, String order, Instant paidAt, int pkg) {
        return new MatchQueue.Entry(consult, order, paidAt, pkg, "呼吸内科");
    }
}
