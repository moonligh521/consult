package com.moonligh.consult.agent.domain.department;

import com.moonligh.consult.agent.domain.CompactText;

/**
 * 科室变更的写路径：延时双删，保证 Redis 最终跟数据库一致。
 *
 * <ol>
 *   <li>先删缓存，避免继续命中旧快照</li>
 *   <li>再写数据库</li>
 *   <li>延迟后再删一次，清掉并发读在中间填回去的旧快照</li>
 * </ol>
 *
 * 第二次删除之后不主动回填。下一次定科按 cache-aside 从库加载新快照。
 */
public class DepartmentCatalogWriter {

    private final DepartmentCache cache;
    private final DepartmentCatalogStore store;
    private final DelayScheduler scheduler;
    private final long doubleDeleteDelayMs;

    private final LexiconWriteRules lexiconRules;

    public DepartmentCatalogWriter(
            DepartmentCache cache,
            DepartmentCatalogStore store,
            DelayScheduler scheduler,
            long doubleDeleteDelayMs) {
        this(cache, store, scheduler, doubleDeleteDelayMs, null);
    }

    public DepartmentCatalogWriter(
            DepartmentCache cache,
            DepartmentCatalogStore store,
            DelayScheduler scheduler,
            long doubleDeleteDelayMs,
            LexiconWriteRules lexiconRules) {
        this.cache = cache;
        this.store = store;
        this.scheduler = scheduler;
        this.doubleDeleteDelayMs = doubleDeleteDelayMs;
        this.lexiconRules = lexiconRules;
    }

    /** 开通或关闭科室。 */
    public void setEnabled(int code, boolean enabled) {
        mutate(() -> store.setEnabled(code, enabled));
    }

    public void upsertTypo(String from, String to) {
        MatchTypo typo = lexiconRules == null
                ? new MatchTypo(CompactText.of(from), CompactText.of(to))
                : lexiconRules.requireTypo(from, to);
        mutate(() -> store.upsertTypo(typo));
    }

    public void upsertUncertainMarker(String marker) {
        String compact = lexiconRules == null
                ? CompactText.of(marker)
                : lexiconRules.requireMarker(marker);
        mutate(() -> store.upsertUncertainMarker(compact));
    }

    public void upsertKeyword(int departmentCode, String keyword, KeywordKind kind) {
        String compact = lexiconRules == null
                ? CompactText.of(keyword)
                : lexiconRules.requireKeyword(keyword);
        mutate(() -> store.upsertKeyword(departmentCode, compact, kind));
    }

    /**
     * 任意目录变更都走同一条双删。手工改 SQL 之后也可以只调 {@link #invalidateEventually()}。
     * 写库失败也安排第二次删除，避免并发读把旧快照填回 Redis。
     */
    public void mutate(Runnable dbWrite) {
        cache.evict();
        try {
            dbWrite.run();
        } finally {
            scheduler.schedule(cache::evict, doubleDeleteDelayMs);
        }
    }

    /** 库已被外部改过时：删一次，延迟再删一次，不写库。 */
    public void invalidateEventually() {
        cache.evict();
        scheduler.schedule(cache::evict, doubleDeleteDelayMs);
    }
}
