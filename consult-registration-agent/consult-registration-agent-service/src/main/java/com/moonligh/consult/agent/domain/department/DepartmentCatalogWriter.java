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

    public int addDepartment(String name, int genderScope, Integer childAgeLt, String treatDesc) {
        int[] code = new int[1];
        mutate(() -> code[0] = store.insertDepartment(name, genderScope, childAgeLt, treatDesc));
        return code[0];
    }

    public long addSpecialty(int departmentCode, String name, String treatDesc) {
        long[] id = new long[1];
        mutate(() -> id[0] = store.insertSpecialty(departmentCode, name, treatDesc));
        return id[0];
    }

    public void setSpecialtyEnabled(long specialtyId, boolean enabled) {
        mutate(() -> store.setSpecialtyEnabled(specialtyId, enabled));
    }

    public void renameSpecialty(long specialtyId, String name) {
        mutate(() -> store.renameSpecialty(specialtyId, name));
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

    public void upsertAmbiguous(String phrase) {
        String compact = lexiconRules == null
                ? CompactText.of(phrase)
                : lexiconRules.requireKeyword(phrase);
        mutate(() -> store.upsertAmbiguous(compact));
    }

    /** 新建或修改 DRAFT 同义规则。目标病名按当前快照校验。 */
    public CanonSynonym saveSynonym(CanonSynonym rule, DepartmentSnapshot snapshot) {
        CanonSynonym checked = lexiconRules == null ? rule : lexiconRules.requireSynonym(rule, snapshot);
        mutate(() -> store.saveSynonym(checked));
        return checked;
    }

    /** DRAFT → REVIEWED，记审核人。之后参与挂科。 */
    public void reviewSynonym(String ruleId, String reviewer) {
        String who = lexiconRules == null ? reviewer : lexiconRules.requireReviewer(reviewer);
        mutate(() -> store.setSynonymStatus(ruleId, SynonymStatus.DRAFT, SynonymStatus.REVIEWED, who));
    }

    /** REVIEWED → RETIRED。 */
    public void retireSynonym(String ruleId) {
        mutate(() -> store.setSynonymStatus(ruleId, SynonymStatus.REVIEWED, SynonymStatus.RETIRED, null));
    }

    /**
     * 写库后回填新快照，延迟再填一次，清掉并发读填回去的旧值。
     * 写库失败也安排第二次回填（或删除），避免脏缓存留着。
     */
    public void mutate(Runnable dbWrite) {
        try {
            dbWrite.run();
            refill();
        } catch (RuntimeException error) {
            cache.evict();
            scheduler.schedule(cache::evict, doubleDeleteDelayMs);
            throw error;
        }
        scheduler.schedule(this::refill, doubleDeleteDelayMs);
    }

    /** 库已被外部改过时：删一次，延迟再删一次，不写库。 */
    public void invalidateEventually() {
        cache.evict();
        scheduler.schedule(cache::evict, doubleDeleteDelayMs);
    }

    private void refill() {
        cache.put(store.load());
    }
}
