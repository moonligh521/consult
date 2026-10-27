package com.moonligh.consult.framework.redis;

import java.util.Map;

import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis Stream 模板。业务只传流名和字段，不直接碰 opsForStream。
 */
public class RedisStreams {

    private final StringRedisTemplate redis;

    public RedisStreams(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 追加一条并按近似上限裁剪。Stream 或 Redis 不可用时返回 null，由调用方走兜底。
     */
    public RecordId addCapped(String stream, Map<String, String> body, long maxlen) {
        if (stream == null || stream.isBlank() || body == null || body.isEmpty()) {
            return null;
        }
        RecordId id = redis.opsForStream().add(stream, body);
        if (id != null && maxlen > 0) {
            redis.opsForStream().trim(stream, maxlen);
        }
        return id;
    }

    /** 消费组已存在时忽略 BUSYGROUP。 */
    public void ensureGroup(String stream, String group) {
        if (stream == null || stream.isBlank() || group == null || group.isBlank()) {
            return;
        }
        try {
            redis.opsForStream().createGroup(stream, ReadOffset.from("0-0"), group);
        } catch (RuntimeException ignored) {
            // BUSYGROUP：组已存在
        }
    }
}
