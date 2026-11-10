package com.moonligh.consult.identity.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import com.moonligh.consult.framework.redis.ConsultRedis;
import com.moonligh.consult.framework.redis.RedisStreams;
import com.moonligh.consult.identity.api.SlotFreed;
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
    public void freed(SlotFreed notice) {
        if (notice == null || notice.specialty() == null || notice.specialty().isBlank()) {
            return;
        }
        try {
            streams.addCapped(ConsultRedis.SLOT_FREED_STREAM, notice.streamFields(), ConsultRedis.SLOT_FREED_MAXLEN);
        } catch (RuntimeException ignored) {
            // Stream 短暂不可用时靠挂号对账把未派单再出一次队
        }
    }
}
