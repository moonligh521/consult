package com.moonligh.consult.agent.domain.department;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moonligh.consult.agent.domain.safety.CatalogLoader;
import com.moonligh.consult.agent.domain.safety.DraftMode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/**
 * 科室变更走延时双删：先删缓存，再写库，延迟后再删一次。
 * 避免并发读把旧快照写回 Redis。
 */
class DepartmentCatalogWriterTest {

    @Test
    void delayedDoubleDeleteRunsDeleteWriteThenDelayedDelete() {
        FakeCache cache = new FakeCache();
        FakeStore store = new FakeStore();
        ManualScheduler scheduler = new ManualScheduler();
        DepartmentCatalogWriter writer = new DepartmentCatalogWriter(cache, store, scheduler, 1000);

        cache.value.set("old-snapshot");
        writer.setEnabled(5, false);

        assertEquals(List.of("delete"), cache.ops);
        assertEquals(List.of("setEnabled:5:false"), store.ops);
        assertEquals(1000, scheduler.delayMs);
        assertEquals(null, cache.value.get());

        cache.ops.clear();
        cache.value.set("stale-fill-from-concurrent-read");
        scheduler.runPending();

        assertEquals(List.of("delete"), cache.ops);
        assertEquals(null, cache.value.get());
        assertTrue(scheduler.pending.isEmpty());
    }

    @Test
    void upsertTypoGoesThroughDelayedDoubleDelete() {
        FakeCache cache = new FakeCache();
        FakeStore store = new FakeStore();
        ManualScheduler scheduler = new ManualScheduler();
        DepartmentCatalogWriter writer = new DepartmentCatalogWriter(cache, store, scheduler, 1000);

        writer.upsertTypo("刻嗽", "咳嗽");

        assertEquals(List.of("delete"), cache.ops);
        assertEquals(List.of("upsertTypo:刻嗽:咳嗽"), store.ops);
        scheduler.runPending();
        assertEquals(List.of("delete", "delete"), cache.ops);
    }

    @Test
    void writeFailureStillSchedulesSecondDelete() {
        FakeCache cache = new FakeCache();
        FakeStore store = new FakeStore();
        store.failNext = true;
        ManualScheduler scheduler = new ManualScheduler();
        DepartmentCatalogWriter writer = new DepartmentCatalogWriter(cache, store, scheduler, 1000);

        assertThrows(IllegalStateException.class, () -> writer.setEnabled(1, false));
        assertEquals(List.of("delete"), cache.ops);
        scheduler.runPending();
        assertEquals(List.of("delete", "delete"), cache.ops);
    }

    @Test
    void rejectsDraftAliasTypoBeforeMutate() {
        FakeCache cache = new FakeCache();
        FakeStore store = new FakeStore();
        ManualScheduler scheduler = new ManualScheduler();
        DepartmentCatalogWriter writer = new DepartmentCatalogWriter(
                cache,
                store,
                scheduler,
                1000,
                new LexiconWriteRules(CatalogLoader.loadDefault(DraftMode.SHADOW)));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> writer.upsertTypo("胸疼", "胸痛"));
        assertTrue(error.getMessage().contains("DRAFT"));
        assertTrue(cache.ops.isEmpty());
        assertTrue(store.ops.isEmpty());
        assertTrue(scheduler.pending.isEmpty());
    }

    private static final class FakeCache implements DepartmentCache {

        private final List<String> ops = new ArrayList<>();
        private final AtomicReference<String> value = new AtomicReference<>("old-snapshot");

        @Override
        public void evict() {
            ops.add("delete");
            value.set(null);
        }
    }

    private static final class FakeStore implements DepartmentCatalogStore {

        private final List<String> ops = new ArrayList<>();
        private boolean failNext;

        @Override
        public void setEnabled(int code, boolean enabled) {
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("db down");
            }
            ops.add("setEnabled:" + code + ":" + enabled);
        }

        @Override
        public void upsertTypo(MatchTypo typo) {
            ops.add("upsertTypo:" + typo.from() + ":" + typo.to());
        }
    }

    private static final class ManualScheduler implements DelayScheduler {

        private final List<Runnable> pending = new ArrayList<>();
        private long delayMs;

        @Override
        public void schedule(Runnable task, long delayMs) {
            this.delayMs = delayMs;
            pending.add(task);
        }

        private void runPending() {
            List<Runnable> due = List.copyOf(pending);
            pending.clear();
            due.forEach(Runnable::run);
        }
    }
}
