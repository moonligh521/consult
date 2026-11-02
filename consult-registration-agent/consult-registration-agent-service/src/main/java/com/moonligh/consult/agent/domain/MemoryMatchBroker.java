package com.moonligh.consult.agent.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * 进程内派单队，给单测和 Redis 不可用时用。重启靠订单表重建。
 */
public final class MemoryMatchBroker implements MatchBroker {

    private final ConcurrentHashMap<String, MatchQueue.Entry> byOrder = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentSkipListSet<String>> lanes = new ConcurrentHashMap<>();
    private final ConcurrentSkipListSet<Delay> delays = new ConcurrentSkipListSet<>();

    @Override
    public void enqueue(MatchQueue.Entry entry, Duration upgradeAfter) {
        if (entry == null || entry.orderId() == null) {
            return;
        }
        MatchQueue.Entry stored = withGrade(entry, entry.queuedGrade() <= 0 ? entry.packageCode() : entry.queuedGrade());
        complete(stored.orderId());
        byOrder.put(stored.orderId(), stored);
        lane(stored.specialty(), stored.queuedGrade()).add(stored.orderId());
        if (stored.queuedGrade() < 4 && stored.paidAt() != null && upgradeAfter != null) {
            delays.add(new Delay(stored.paidAt().plus(upgradeAfter), stored.orderId()));
        }
    }

    @Override
    public MatchQueue.Entry pop(String specialty, int grade) {
        ConcurrentSkipListSet<String> lane = lanes.get(key(specialty, grade));
        if (lane == null) {
            return null;
        }
        String orderId = lane.pollFirst();
        if (orderId == null) {
            return null;
        }
        return byOrder.get(orderId);
    }

    @Override
    public void restore(MatchQueue.Entry entry) {
        if (entry == null || entry.orderId() == null) {
            return;
        }
        byOrder.put(entry.orderId(), entry);
        lane(entry.specialty(), entry.queuedGrade()).add(entry.orderId());
    }

    @Override
    public boolean contains(String orderId) {
        return orderId != null && byOrder.containsKey(orderId);
    }

    @Override
    public MatchQueue.Entry ensure(MatchQueue.Entry entry, Duration upgradeAfter) {
        if (entry == null || entry.orderId() == null) {
            return null;
        }
        MatchQueue.Entry current = byOrder.get(entry.orderId());
        if (current != null) {
            lane(current.specialty(), current.queuedGrade()).add(current.orderId());
            return current;
        }
        enqueue(entry, upgradeAfter);
        return byOrder.get(entry.orderId());
    }

    @Override
    public void complete(String orderId) {
        if (orderId == null) {
            return;
        }
        MatchQueue.Entry entry = byOrder.remove(orderId);
        delays.removeIf(item -> orderId.equals(item.orderId()));
        if (entry == null) {
            lanes.values().forEach(lane -> lane.remove(orderId));
            return;
        }
        ConcurrentSkipListSet<String> lane = lanes.get(key(entry.specialty(), entry.queuedGrade()));
        if (lane != null) {
            lane.remove(orderId);
        }
    }

    @Override
    public List<MatchQueue.Entry> popDueUpgrades(Instant now, int limit) {
        int cap = limit <= 0 ? Integer.MAX_VALUE : limit;
        List<MatchQueue.Entry> due = new ArrayList<>();
        while (due.size() < cap && !delays.isEmpty()) {
            Delay first = delays.first();
            if (first.at().isAfter(now)) {
                break;
            }
            if (!delays.remove(first)) {
                continue;
            }
            MatchQueue.Entry entry = byOrder.get(first.orderId());
            if (entry != null && entry.queuedGrade() < 4 && entry.queuedGrade() == entry.packageCode()) {
                due.add(entry);
            }
        }
        return due;
    }

    @Override
    public void promote(MatchQueue.Entry entry) {
        if (entry == null || entry.queuedGrade() >= 4) {
            return;
        }
        MatchQueue.Entry current = byOrder.get(entry.orderId());
        if (current == null) {
            return;
        }
        ConcurrentSkipListSet<String> old = lanes.get(key(current.specialty(), current.queuedGrade()));
        if (old != null) {
            old.remove(current.orderId());
        }
        MatchQueue.Entry next = current.promoted();
        byOrder.put(next.orderId(), next);
        lane(next.specialty(), next.queuedGrade()).add(next.orderId());
    }

    private ConcurrentSkipListSet<String> lane(String specialty, int grade) {
        return lanes.computeIfAbsent(key(specialty, grade), ignored -> new ConcurrentSkipListSet<>(this::compareOrders));
    }

    private int compareOrders(String left, String right) {
        MatchQueue.Entry a = byOrder.get(left);
        MatchQueue.Entry b = byOrder.get(right);
        if (a == null && b == null) {
            return left.compareTo(right);
        }
        if (a == null) {
            return 1;
        }
        if (b == null) {
            return -1;
        }
        return MatchQueue.compareFifo(a, b);
    }

    private static MatchQueue.Entry withGrade(MatchQueue.Entry entry, int grade) {
        return new MatchQueue.Entry(
                entry.consultationId(),
                entry.orderId(),
                entry.paidAt(),
                entry.packageCode(),
                entry.specialty(),
                grade);
    }

    private static String key(String specialty, int grade) {
        return (specialty == null ? "" : specialty) + "\u0001" + grade;
    }

    private record Delay(Instant at, String orderId) implements Comparable<Delay> {
        @Override
        public int compareTo(Delay other) {
            int byTime = at.compareTo(other.at);
            if (byTime != 0) {
                return byTime;
            }
            return orderId.compareTo(other.orderId);
        }
    }
}
