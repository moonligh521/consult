package com.moonligh.consult.agent.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class MemoryMatchBrokerTest {

    private static final Instant T0 = Instant.parse("2026-10-27T11:00:00Z");
    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);

    @Test
    void popsInPaymentOrder() {
        MemoryMatchBroker broker = new MemoryMatchBroker();
        broker.enqueue(entry("c2", "o2", T0.plusSeconds(20)), ONE_MINUTE);
        broker.enqueue(entry("c1", "o1", T0), ONE_MINUTE);
        broker.enqueue(entry("c3", "o3", T0.plusSeconds(40)), ONE_MINUTE);
        assertEquals("o1", broker.pop("呼吸内科", 3).orderId());
        assertEquals("o2", broker.pop("呼吸内科", 3).orderId());
        assertEquals("o3", broker.pop("呼吸内科", 3).orderId());
        assertNull(broker.pop("呼吸内科", 3));
    }

    @Test
    void restoreKeepsOriginalPlace() {
        MemoryMatchBroker broker = new MemoryMatchBroker();
        MatchQueue.Entry first = entry("c1", "o1", T0);
        MatchQueue.Entry second = entry("c2", "o2", T0.plusSeconds(1));
        broker.enqueue(first, ONE_MINUTE);
        broker.enqueue(second, ONE_MINUTE);
        MatchQueue.Entry popped = broker.pop("呼吸内科", 3);
        broker.restore(popped);
        assertEquals("o1", broker.pop("呼吸内科", 3).orderId());
    }

    @Test
    void popDueUpgradesTakesAllWaitedOneMinuteAndDeletes() {
        MemoryMatchBroker broker = new MemoryMatchBroker();
        broker.enqueue(entry("c1", "o1", T0), ONE_MINUTE);
        broker.enqueue(entry("c2", "o2", T0.plusSeconds(10)), ONE_MINUTE);
        broker.enqueue(entry("c3", "o3", T0.plusSeconds(90)), ONE_MINUTE);
        List<MatchQueue.Entry> due = broker.popDueUpgrades(T0.plusSeconds(70), 0);
        assertEquals(List.of("o1", "o2"), due.stream().map(MatchQueue.Entry::orderId).toList());
        assertEquals(List.of(), broker.popDueUpgrades(T0.plusSeconds(70), 0).stream().map(MatchQueue.Entry::orderId).toList());
        assertEquals("o3", broker.popDueUpgrades(T0.plusSeconds(151), 0).get(0).orderId());
    }

    @Test
    void promoteMovesToNextGradeKeepingPayTime() {
        MemoryMatchBroker broker = new MemoryMatchBroker();
        broker.enqueue(entry("c1", "o1", T0), ONE_MINUTE);
        broker.enqueue(new MatchQueue.Entry("c4", "o4", T0.plusSeconds(10), 4, "呼吸内科"), ONE_MINUTE);
        List<MatchQueue.Entry> due = broker.popDueUpgrades(T0.plusSeconds(61), 10);
        assertEquals(1, due.size());
        broker.promote(due.get(0));
        assertEquals("o1", broker.pop("呼吸内科", 4).orderId());
        assertEquals("o4", broker.pop("呼吸内科", 4).orderId());
    }

    @Test
    void completeRemovesFromLane() {
        MemoryMatchBroker broker = new MemoryMatchBroker();
        broker.enqueue(entry("c1", "o1", T0), ONE_MINUTE);
        assertTrue(broker.contains("o1"));
        broker.complete("o1");
        assertFalse(broker.contains("o1"));
        assertNull(broker.pop("呼吸内科", 3));
    }

    @Test
    void containsStaysTrueAfterPopUntilComplete() {
        MemoryMatchBroker broker = new MemoryMatchBroker();
        broker.enqueue(entry("c1", "o1", T0), ONE_MINUTE);
        assertEquals("o1", broker.pop("呼吸内科", 3).orderId());
        assertTrue(broker.contains("o1"));
        MatchQueue.Entry live = broker.ensure(new MatchQueue.Entry("c1", "o1", T0, 3, "呼吸内科"), ONE_MINUTE);
        assertEquals(3, live.queuedGrade());
        assertEquals("o1", broker.pop("呼吸内科", 3).orderId());
    }

    private static MatchQueue.Entry entry(String consult, String order, Instant paidAt) {
        return new MatchQueue.Entry(consult, order, paidAt, 3, "呼吸内科");
    }
}
