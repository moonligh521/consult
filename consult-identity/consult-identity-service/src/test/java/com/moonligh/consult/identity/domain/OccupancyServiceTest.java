package com.moonligh.consult.identity.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class OccupancyServiceTest {

    private final Instant start = Instant.parse("2026-10-26T12:00:00Z");
    private final MutableClock clock = new MutableClock(start);
    private final MemoryOccupancyStore store = new MemoryOccupancyStore();
    private final AtomicInteger flights = new AtomicInteger(1);
    private final OccupancyService occupancy = new OccupancyService(store, ignored -> flights.decrementAndGet(), clock);

    @Test
    void occupyWritesActiveRowWithExpiry() {
        occupancy.occupy("ord-1", "doc-1", "sch-1", 15);
        OccupancyRow row = store.find("ord-1");
        assertEquals(OccupancyRow.ACTIVE, row.status());
        assertEquals(start.plusSeconds(15 * 60), row.expiresAt());
        assertNull(row.releasedAt());
    }

    @Test
    void secondOccupySameOrderIsIdempotent() {
        occupancy.occupy("ord-1", "doc-1", "sch-1", 15);
        occupancy.occupy("ord-1", "doc-1", "sch-1", 30);
        assertEquals(1, store.size());
        assertEquals(start.plusSeconds(15 * 60), store.find("ord-1").expiresAt());
    }

    @Test
    void endReleasesOnceThenNoOp() {
        occupancy.occupy("ord-1", "doc-1", "sch-1", 15);
        assertTrue(occupancy.release("ord-1", OccupancyRow.ENDED));
        assertEquals(0, flights.get());
        assertEquals(OccupancyRow.ENDED, store.find("ord-1").status());
        assertFalse(occupancy.release("ord-1", OccupancyRow.ENDED));
        assertEquals(0, flights.get());
    }

    @Test
    void extendMovesExpiryWhileActive() {
        occupancy.occupy("ord-1", "doc-1", "sch-1", 15);
        assertTrue(occupancy.extend("ord-1", 30));
        assertEquals(start.plusSeconds(30 * 60), store.find("ord-1").expiresAt());
        occupancy.release("ord-1", OccupancyRow.ENDED);
        assertFalse(occupancy.extend("ord-1", 10));
    }

    @Test
    void reaperTimesOutExpiredAndFreesFlight() {
        occupancy.occupy("ord-1", "doc-1", "sch-1", 15);
        clock.set(start.plusSeconds(15 * 60 + 1));
        assertEquals(1, occupancy.reapBatch(200));
        assertEquals(OccupancyRow.TIMEOUT, store.find("ord-1").status());
        assertEquals(0, flights.get());
        assertEquals(0, occupancy.reapBatch(200));
    }

    @Test
    void reapBatchStopsAtLimitAndOldestFirst() {
        flights.set(3);
        occupancy.occupy("ord-old", "doc-1", "sch-1", 15);
        clock.set(start.plusSeconds(60));
        occupancy.occupy("ord-mid", "doc-1", "sch-1", 15);
        clock.set(start.plusSeconds(120));
        occupancy.occupy("ord-new", "doc-1", "sch-1", 15);
        clock.set(start.plusSeconds(15 * 60 + 121));
        assertEquals(2, occupancy.reapBatch(2));
        assertEquals(OccupancyRow.TIMEOUT, store.find("ord-old").status());
        assertEquals(OccupancyRow.TIMEOUT, store.find("ord-mid").status());
        assertEquals(OccupancyRow.ACTIVE, store.find("ord-new").status());
        assertEquals(1, occupancy.reapBatch(200));
        assertEquals(OccupancyRow.TIMEOUT, store.find("ord-new").status());
        assertEquals(0, occupancy.reapBatch(200));
    }

    private static final class MutableClock extends Clock {
        private Instant now;
        private MutableClock(Instant now) {
            this.now = now;
        }
        void set(Instant value) {
            this.now = value;
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
            return now;
        }
    }

    private static final class MemoryOccupancyStore implements OccupancyStore {
        private final ConcurrentHashMap<String, OccupancyRow> rows = new ConcurrentHashMap<>();
        @Override
        public OccupancyRow find(String orderId) {
            return rows.get(orderId);
        }
        @Override
        public void insert(OccupancyRow row) {
            rows.putIfAbsent(row.orderId(), row);
        }
        @Override
        public boolean markReleased(String orderId, int reason, Instant at) {
            OccupancyRow current = rows.get(orderId);
            if (current == null || current.status() != OccupancyRow.ACTIVE) {
                return false;
            }
            rows.put(orderId, current.released(reason, at));
            return true;
        }
        @Override
        public boolean updateExpiry(String orderId, Instant expiresAt) {
            OccupancyRow current = rows.get(orderId);
            if (current == null || current.status() != OccupancyRow.ACTIVE) {
                return false;
            }
            rows.put(orderId, current.withExpiresAt(expiresAt));
            return true;
        }
        @Override
        public List<OccupancyRow> claimExpired(Instant now, int limit) {
            List<OccupancyRow> out = new ArrayList<>();
            for (OccupancyRow row : rows.values()) {
                if (row.status() == OccupancyRow.ACTIVE && !row.expiresAt().isAfter(now)) {
                    out.add(row);
                }
            }
            out.sort(Comparator.comparing(OccupancyRow::expiresAt));
            if (limit > 0 && limit < out.size()) {
                return List.copyOf(out.subList(0, limit));
            }
            return out;
        }
        int size() {
            return rows.size();
        }
    }
}
