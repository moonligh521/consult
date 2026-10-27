package com.moonligh.consult.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.moonligh.consult.agent.config.InquiryProperties;
import com.moonligh.consult.agent.domain.MatchQueue;
import com.moonligh.consult.agent.domain.MemoryMatchBroker;

class MatchDispatcherTest {

    private static final Instant T0 = Instant.parse("2026-10-27T11:00:00Z");

    @Test
    void tenPayersFifoFirstThreeGetSlots() {
        AtomicInteger slots = new AtomicInteger(3);
        List<String> assigned = new ArrayList<>();
        MatchDispatcher dispatcher = dispatcher(T0, entry -> {
            if (entry.queuedGrade() != 3 || slots.get() <= 0) {
                return false;
            }
            slots.decrementAndGet();
            assigned.add(entry.orderId());
            return true;
        });
        for (int i = 1; i <= 10; i++) {
            dispatcher.offer(entry("c" + i, "o" + i, T0.plusSeconds(i), 3, 3));
        }
        assertEquals(List.of("o1", "o2", "o3"), assigned);

        slots.set(1);
        dispatcher.onSlotFreed("呼吸内科", 3);
        assertEquals(List.of("o1", "o2", "o3", "o4"), assigned);
    }

    @Test
    void laterPayerDoesNotJumpHead() {
        Set<String> assigned = ConcurrentHashMap.newKeySet();
        MatchDispatcher dispatcher = dispatcher(T0, entry -> {
            if (!"o1".equals(entry.orderId())) {
                assigned.add(entry.orderId());
                return true;
            }
            return false;
        });
        dispatcher.offer(entry("c1", "o1", T0, 3, 3));
        dispatcher.offer(entry("c2", "o2", T0.plusSeconds(1), 3, 3));
        assertFalse(assigned.contains("o2"));
    }

    @Test
    void restoreAllDispatchesEachLaneOnce() {
        AtomicInteger occupies = new AtomicInteger();
        MatchDispatcher dispatcher = dispatcher(T0, entry -> {
            occupies.incrementAndGet();
            return occupies.get() <= 1;
        });
        dispatcher.restoreAll(List.of(
                entry("c1", "o1", T0, 3, 3),
                entry("c2", "o2", T0.plusSeconds(1), 3, 3)));
        // 同一档只出一次队：占上队头后下一单没坑就停，不会跳过 o2
        assertEquals(2, occupies.get());
    }

    @Test
    void reconcilePutsPoppedHeadBackWithoutLosingPlace() {
        List<String> assigned = new ArrayList<>();
        MemoryMatchBroker broker = new MemoryMatchBroker();
        InquiryProperties inquiry = new InquiryProperties();
        inquiry.setMatchUpgradeAfter(Duration.ofMinutes(1));
        MatchDispatcher dispatcher = new MatchDispatcher(broker, entry -> {
            assigned.add(entry.orderId());
            return true;
        }, inquiry, Clock.fixed(T0, ZoneOffset.UTC));
        MatchQueue.Entry first = entry("c1", "o1", T0, 3, 3);
        MatchQueue.Entry second = entry("c2", "o2", T0.plusSeconds(1), 3, 3);
        broker.enqueue(first, Duration.ofMinutes(1));
        broker.enqueue(second, Duration.ofMinutes(1));
        broker.pop("呼吸内科", 3);
        dispatcher.reconcile(List.of(first, second));
        assertEquals(List.of("o1", "o2"), assigned);
    }

    @Test
    void reconcileAfterPromoteKeepsUpgradedGrade() {
        MutableClock clock = new MutableClock(T0);
        List<Integer> grades = new ArrayList<>();
        MatchDispatcher dispatcher = dispatcher(clock, entry -> {
            grades.add(entry.queuedGrade());
            return false;
        });
        dispatcher.offer(entry("c1", "o1", T0, 3, 3));
        clock.set(T0.plusSeconds(61));
        dispatcher.promoteDue();
        grades.clear();
        dispatcher.reconcile(List.of(new MatchQueue.Entry("c1", "o1", T0, 3, "呼吸内科")));
        assertEquals(List.of(4), grades);
    }

    @Test
    void reconcileDoesNotReenqueueSoUpgradeDeadlineStaysOnPayTime() {
        MutableClock clock = new MutableClock(T0);
        List<String> experts = new ArrayList<>();
        MatchDispatcher dispatcher = dispatcher(clock, entry -> {
            if (entry.queuedGrade() == 4) {
                experts.add(entry.orderId());
                return true;
            }
            return false;
        });
        MatchQueue.Entry waiting = entry("c1", "o1", T0, 3, 3);
        dispatcher.offer(waiting);
        dispatcher.reconcile(List.of(waiting));
        clock.set(T0.plusSeconds(61));
        dispatcher.promoteDue();
        assertEquals(List.of("o1"), experts);
    }

    @Test
    void afterOneMinuteHeadPromotesAndTakesHigherGradeSlot() {
        MutableClock clock = new MutableClock(T0);
        List<String> experts = new ArrayList<>();
        MatchDispatcher dispatcher = dispatcher(clock, entry -> {
            if (entry.queuedGrade() == 4) {
                experts.add(entry.orderId());
                return true;
            }
            return false;
        });
        dispatcher.offer(entry("c1", "o1", T0, 3, 3));
        assertTrue(experts.isEmpty());
        clock.set(T0.plusSeconds(61));
        dispatcher.promoteDue();
        assertEquals(List.of("o1"), experts);
    }

    private static MatchDispatcher dispatcher(Instant now, MatchDispatcher.Occupier occupier) {
        return dispatcher(Clock.fixed(now, ZoneOffset.UTC), occupier);
    }

    private static MatchDispatcher dispatcher(Clock clock, MatchDispatcher.Occupier occupier) {
        InquiryProperties inquiry = new InquiryProperties();
        inquiry.setMatchUpgradeAfter(Duration.ofMinutes(1));
        return new MatchDispatcher(new MemoryMatchBroker(), occupier, inquiry, clock);
    }

    private static MatchQueue.Entry entry(String consult, String order, Instant paidAt, int pkg, int grade) {
        return new MatchQueue.Entry(consult, order, paidAt, pkg, "呼吸内科", grade);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
