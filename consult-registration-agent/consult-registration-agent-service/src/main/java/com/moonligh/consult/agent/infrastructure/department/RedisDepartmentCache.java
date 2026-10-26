package com.moonligh.consult.agent.infrastructure.department;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonligh.consult.agent.domain.department.DepartmentCache;
import com.moonligh.consult.agent.domain.department.DepartmentRecord;
import com.moonligh.consult.agent.domain.department.DepartmentSnapshot;
import com.moonligh.consult.agent.domain.department.MatchTypo;

/**
 * Redis + 进程内快照。写路径只 {@link #evict()}，读路径 miss 再回源。
 */
public class RedisDepartmentCache implements DepartmentCache {

    private static final Logger log = LoggerFactory.getLogger(RedisDepartmentCache.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final String key;
    private final Duration redisTtl;
    private final long localTtlMs;
    private final AtomicReference<Holder> local = new AtomicReference<>();

    public RedisDepartmentCache(
            StringRedisTemplate redis,
            ObjectMapper mapper,
            String key,
            Duration redisTtl,
            long localTtlMs) {
        this.redis = redis;
        this.mapper = mapper;
        this.key = key;
        this.redisTtl = redisTtl;
        this.localTtlMs = localTtlMs;
    }

    @Override
    public void evict() {
        local.set(null);
        try {
            redis.delete(key);
        } catch (RuntimeException error) {
            log.warn("删除科室目录 Redis 缓存失败 key={}", key, error);
        }
    }

    /** cache-aside：本地 → Redis → 数据库回源后回填。 */
    public DepartmentSnapshot load(java.util.function.Supplier<DepartmentSnapshot> source) {
        Holder cached = local.get();
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.loadedAt < localTtlMs) {
            return cached.snapshot;
        }
        synchronized (this) {
            cached = local.get();
            now = System.currentTimeMillis();
            if (cached != null && now - cached.loadedAt < localTtlMs) {
                return cached.snapshot;
            }
            DepartmentSnapshot fromRedis = readRedis();
            if (fromRedis != null) {
                local.set(new Holder(fromRedis, now));
                return fromRedis;
            }
            DepartmentSnapshot loaded = source.get();
            writeRedis(loaded);
            local.set(new Holder(loaded, System.currentTimeMillis()));
            return loaded;
        }
    }

    private DepartmentSnapshot readRedis() {
        try {
            String json = redis.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return null;
            }
            CachePayload payload = mapper.readValue(json, CachePayload.class);
            if (payload.typos() == null || payload.uncertain() == null
                    || payload.ambiguous() == null || payload.synonyms() == null) {
                return null;
            }
            return new DepartmentSnapshot(
                    payload.version(), payload.departments(), payload.typos(), payload.uncertain(),
                    payload.ambiguous(), payload.synonyms());
        } catch (Exception error) {
            log.warn("读取科室目录 Redis 缓存失败 key={}", key, error);
            return null;
        }
    }

    private void writeRedis(DepartmentSnapshot snapshot) {
        try {
            CachePayload payload = new CachePayload(
                    snapshot.version(), snapshot.enabled(), snapshot.typos(), snapshot.uncertainMarkers(),
                    snapshot.ambiguousSymptoms(), snapshot.synonyms());
            redis.opsForValue().set(key, mapper.writeValueAsString(payload), redisTtl);
        } catch (RuntimeException | JsonProcessingException error) {
            log.warn("写入科室目录 Redis 缓存失败 key={}", key, error);
        }
    }

    private record Holder(DepartmentSnapshot snapshot, long loadedAt) {
    }

    private record CachePayload(
            String version,
            java.util.List<DepartmentRecord> departments,
            java.util.List<MatchTypo> typos,
            java.util.List<String> uncertain,
            java.util.List<String> ambiguous,
            java.util.List<com.moonligh.consult.agent.domain.department.CanonSynonym> synonyms) {
    }
}
