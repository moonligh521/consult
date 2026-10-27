package com.moonligh.consult.identity.infrastructure;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.moonligh.consult.identity.domain.SlotFreedSink;

/**
 * 占坑释放写入 Redis Stream consult:stream:slot-freed，挂号消费组可靠出队。
 * 不再用 Pub/Sub：发出去不落盘，订阅者重启会丢。
 */
@Component
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisSlotFreedSink implements SlotFreedSink {

    public static final String STREAM = "consult:stream:slot-freed";
    static final long MAXLEN = 20_000;

    private final StringRedisTemplate redis;

    public RedisSlotFreedSink(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void freed(String specialty, int grade) {
        if (specialty == null || specialty.isBlank() || redis == null) {
            return;
        }
        try {
            Map<String, String> body = new LinkedHashMap<>();
            body.put("specialty", specialty);
            body.put("grade", Integer.toString(grade));
            RecordId id = redis.opsForStream().add(STREAM, body);
            if (id != null) {
                redis.opsForStream().trim(STREAM, MAXLEN);
            }
        } catch (RuntimeException ignored) {
            // Stream 短暂不可用时靠挂号对账把未派单再出一次队
        }
    }
}
