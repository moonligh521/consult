package com.moonligh.consult.identity.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.moonligh.consult.identity.domain.SlotFreedSink;

/**
 * 占坑释放后通知挂号服务：频道 consult:slot-freed，内容「二级科\\t档位」。
 */
@Component
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisSlotFreedSink implements SlotFreedSink {

    public static final String CHANNEL = "consult:slot-freed";

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
            redis.convertAndSend(CHANNEL, specialty + "\t" + grade);
        } catch (RuntimeException ignored) {
            // Redis 短暂不可用时队头等下一拍提档或下次放号
        }
    }
}
