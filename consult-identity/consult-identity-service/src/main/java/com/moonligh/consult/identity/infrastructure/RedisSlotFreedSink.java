package com.moonligh.consult.identity.infrastructure;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import com.moonligh.consult.framework.redis.ConsultRedis;
import com.moonligh.consult.framework.redis.RedisStreams;
import com.moonligh.consult.identity.domain.SlotFreedSink;

/**
 * 占坑释放写入 Redis Stream consult:stream:slot-freed，挂号消费组可靠出队。
 * 不再用 Pub/Sub：发出去不落盘，订阅者重启会丢。
 */
@Component
@ConditionalOnBean(RedisStreams.class)
public class RedisSlotFreedSink implements SlotFreedSink {

    private final RedisStreams streams;

    public RedisSlotFreedSink(RedisStreams streams) {
        this.streams = streams;
    }

    @Override
    public void freed(String specialty, int grade) {
        if (specialty == null || specialty.isBlank()) {
            return;
        }
        try {
            Map<String, String> body = new LinkedHashMap<>();
            body.put("specialty", specialty);
            body.put("grade", Integer.toString(grade));
            streams.addCapped(ConsultRedis.SLOT_FREED_STREAM, body, ConsultRedis.SLOT_FREED_MAXLEN);
        } catch (RuntimeException ignored) {
            // Stream 短暂不可用时靠挂号对账把未派单再出一次队
        }
    }
}
