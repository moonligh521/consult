package com.moonligh.consult.agent.infrastructure.match;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.redisson.api.RBucket;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.protocol.ScoredEntry;

import com.moonligh.consult.agent.domain.MatchBroker;
import com.moonligh.consult.agent.domain.MatchQueue;

/**
 * Redis ZSET 派单队。分数是支付时间毫秒，同一毫秒按订单号（UUIDv7）排序。
 */
public final class RedissonMatchBroker implements MatchBroker {

    static final String PREFIX = "consult:match:";

    private final RedissonClient redisson;

    public RedissonMatchBroker(RedissonClient redisson) {
        this.redisson = redisson;
    }

    @Override
    public void enqueue(MatchQueue.Entry entry, Duration upgradeAfter) {
        if (entry == null || entry.orderId() == null) {
            return;
        }
        MatchQueue.Entry stored = new MatchQueue.Entry(
                entry.consultationId(),
                entry.orderId(),
                entry.paidAt(),
                entry.packageCode(),
                entry.specialty(),
                entry.queuedGrade() <= 0 ? entry.packageCode() : entry.queuedGrade());
        complete(stored.orderId());
        meta(stored.orderId()).set(encode(stored));
        lane(stored.specialty(), stored.queuedGrade()).add(score(stored), stored.orderId());
        if (stored.queuedGrade() < 4 && stored.paidAt() != null && upgradeAfter != null) {
            delay().add(stored.paidAt().plus(upgradeAfter).toEpochMilli(), stored.orderId());
        }
    }

    @Override
    public MatchQueue.Entry pop(String specialty, int grade) {
        RScoredSortedSet<Object> set = lane(specialty, grade);
        for (int i = 0; i < 32; i++) {
            Object member = set.pollFirst();
            if (member == null) {
                return null;
            }
            MatchQueue.Entry entry = load(String.valueOf(member));
            if (entry != null) {
                return entry;
            }
        }
        return null;
    }

    @Override
    public void restore(MatchQueue.Entry entry) {
        if (entry == null || entry.orderId() == null) {
            return;
        }
        meta(entry.orderId()).set(encode(entry));
        lane(entry.specialty(), entry.queuedGrade()).add(score(entry), entry.orderId());
    }

    @Override
    public boolean contains(String orderId) {
        return orderId != null && Boolean.TRUE.equals(meta(orderId).isExists());
    }

    @Override
    public MatchQueue.Entry ensure(MatchQueue.Entry entry, Duration upgradeAfter) {
        if (entry == null || entry.orderId() == null) {
            return null;
        }
        MatchQueue.Entry current = load(entry.orderId());
        if (current != null) {
            lane(current.specialty(), current.queuedGrade()).add(score(current), current.orderId());
            return current;
        }
        enqueue(entry, upgradeAfter);
        return load(entry.orderId());
    }

    @Override
    public void complete(String orderId) {
        if (orderId == null) {
            return;
        }
        MatchQueue.Entry entry = load(orderId);
        delay().remove(orderId);
        if (entry != null) {
            lane(entry.specialty(), entry.queuedGrade()).remove(orderId);
        }
        meta(orderId).delete();
    }

    @Override
    public List<MatchQueue.Entry> popDueUpgrades(Instant now, int limit) {
        long until = now == null ? 0 : now.toEpochMilli();
        RScoredSortedSet<Object> delayed = delay();
        // ZRANGEBYSCORE delay 0 now：分数是 paidAt+1分钟，到期的一次拿走
        Iterable<ScoredEntry<Object>> items = limit <= 0
                ? delayed.entryRange(0, true, until, true)
                : delayed.entryRange(0, true, until, true, 0, limit);
        List<MatchQueue.Entry> due = new ArrayList<>();
        for (ScoredEntry<Object> item : items) {
            if (item == null || item.getValue() == null) {
                continue;
            }
            String orderId = String.valueOf(item.getValue());
            if (!delayed.remove(orderId)) {
                continue;
            }
            MatchQueue.Entry entry = load(orderId);
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
        MatchQueue.Entry current = load(entry.orderId());
        if (current == null) {
            return;
        }
        lane(current.specialty(), current.queuedGrade()).remove(current.orderId());
        MatchQueue.Entry next = current.promoted();
        meta(next.orderId()).set(encode(next));
        lane(next.specialty(), next.queuedGrade()).add(score(next), next.orderId());
    }

    private MatchQueue.Entry load(String orderId) {
        String raw = meta(orderId).get();
        return raw == null ? null : decode(raw);
    }

    private RScoredSortedSet<Object> lane(String specialty, int grade) {
        return redisson.getScoredSortedSet(PREFIX + "q:" + (specialty == null ? "" : specialty) + ":" + grade);
    }

    private RScoredSortedSet<Object> delay() {
        return redisson.getScoredSortedSet(PREFIX + "delay");
    }

    private RBucket<String> meta(String orderId) {
        return redisson.getBucket(PREFIX + "meta:" + orderId);
    }

    private static double score(MatchQueue.Entry entry) {
        long millis = entry.paidAt() == null ? 0 : entry.paidAt().toEpochMilli();
        return millis;
    }

    static String encode(MatchQueue.Entry entry) {
        return String.join("\n",
                nullToEmpty(entry.consultationId()),
                nullToEmpty(entry.orderId()),
                entry.paidAt() == null ? "0" : Long.toString(entry.paidAt().toEpochMilli()),
                Integer.toString(entry.packageCode()),
                nullToEmpty(entry.specialty()),
                Integer.toString(entry.queuedGrade()));
    }

    static MatchQueue.Entry decode(String raw) {
        String[] parts = raw.split("\n", -1);
        if (parts.length < 6) {
            return null;
        }
        Instant paidAt = Instant.ofEpochMilli(Long.parseLong(parts[2]));
        return new MatchQueue.Entry(
                parts[0],
                parts[1],
                paidAt,
                Integer.parseInt(parts[3]),
                parts[4],
                Integer.parseInt(parts[5]));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
