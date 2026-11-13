package com.moonligh.consult.agent.infrastructure.department;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonligh.consult.agent.domain.department.CatalogDriftException;
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
            redis.delete(versionKey());
        } catch (RuntimeException error) {
            log.warn("删除科室目录 Redis 缓存失败 key={}", key, error);
        }
    }

    @Override
    public void put(DepartmentSnapshot snapshot) {
        if (snapshot == null) {
            evict();
            return;
        }
        writeRedis(snapshot);
        local.set(new Holder(snapshot, System.currentTimeMillis()));
    }

    /** cache-aside：本地（校验 Redis 版本）→ Redis → 数据库回源后回填。 */
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
            CacheRead redisRead = readRedis();
            if (redisRead.snapshot != null) {
                local.set(new Holder(redisRead.snapshot, now));
                return redisRead.snapshot;
            }
            if (redisRead.failed && cached != null) {
                // Redis 抖动时继续使用最后一次成功快照，避免所有请求串行等待 Redis + DB。
                local.set(new Holder(cached.snapshot, now));
                return cached.snapshot;
            }
            DepartmentSnapshot loaded;
            try {
                loaded = source.get();
            } catch (CatalogDriftException error) {
                log.error("目录对账失败，保留上一份成功快照: {}", error.getMessage());
                try {
                    redis.delete(key);
                } catch (RuntimeException ignored) {
                    // 清掉可能过期的 Redis 副本；本地成功快照仍然可用。
                }
                if (cached != null) {
                    local.set(new Holder(cached.snapshot, now));
                    return cached.snapshot;
                }
                throw error;
            }
            writeRedis(loaded);
            local.set(new Holder(loaded, System.currentTimeMillis()));
            return loaded;
        }
    }

    private String versionKey() {
        return key + ":ver";
    }

    private CacheRead readRedis() {
        try {
            String json = redis.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return CacheRead.miss();
            }
            CachePayload payload = mapper.readValue(json, CachePayload.class);
            if (payload.typos() == null || payload.uncertain() == null
                    || payload.ambiguous() == null || payload.synonyms() == null) {
                return CacheRead.miss();
            }
            return CacheRead.hit(new DepartmentSnapshot(
                    payload.version(), payload.departments(), payload.typos(), payload.uncertain(),
                    payload.ambiguous(), payload.synonyms()));
        } catch (Exception error) {
            log.warn("读取科室目录 Redis 缓存失败 key={} type={}", key, error.getClass().getSimpleName());
            return CacheRead.failure();
        }
    }

    private void writeRedis(DepartmentSnapshot snapshot) {
        try {
            CachePayload payload = new CachePayload(
                    snapshot.version(), snapshot.enabled(), snapshot.typos(), snapshot.uncertainMarkers(),
                    snapshot.ambiguousSymptoms(), snapshot.synonyms());
            redis.opsForValue().set(key, mapper.writeValueAsString(payload), redisTtl);
            redis.opsForValue().set(versionKey(), snapshot.version(), redisTtl);
        } catch (RuntimeException | JsonProcessingException error) {
            log.warn("写入科室目录 Redis 缓存失败 key={}", key, error);
        }
    }

    private record Holder(DepartmentSnapshot snapshot, long loadedAt) {
    }

    private record CacheRead(DepartmentSnapshot snapshot, boolean failed) {
        private static CacheRead hit(DepartmentSnapshot snapshot) {
            return new CacheRead(snapshot, false);
        }

        private static CacheRead miss() {
            return new CacheRead(null, false);
        }

        private static CacheRead failure() {
            return new CacheRead(null, true);
        }
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
