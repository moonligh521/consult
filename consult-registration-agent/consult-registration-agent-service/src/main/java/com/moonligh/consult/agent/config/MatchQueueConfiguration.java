package com.moonligh.consult.agent.config;

import java.time.Clock;

import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import com.moonligh.consult.agent.domain.MatchBroker;
import com.moonligh.consult.agent.domain.MemoryMatchBroker;
import com.moonligh.consult.agent.infrastructure.match.RedissonMatchBroker;
import com.moonligh.consult.agent.service.ConsultationService;
import com.moonligh.consult.agent.service.MatchDispatcher;

/**
 * 派医生队：Redis ZSET 按支付时间排队；没有 Redisson 时用内存队。
 * 放号走 Redis 频道 consult:slot-freed；满 1 分钟提档由 XXL matchUpgradeJob 扫 delay。
 */
@Configuration
@ConditionalOnProperty(prefix = "inquiry", name = "match-queue-enabled", havingValue = "true", matchIfMissing = true)
public class MatchQueueConfiguration {

    public static final String SLOT_FREED_CHANNEL = "consult:slot-freed";

    @Bean
    public Clock matchClock() {
        return Clock.systemUTC();
    }

    @Bean
    public MatchBroker matchBroker(ObjectProvider<RedissonClient> redisson) {
        RedissonClient client = redisson.getIfAvailable();
        return client == null ? new MemoryMatchBroker() : new RedissonMatchBroker(client);
    }

    @Bean
    public MatchDispatcher matchDispatcher(
            MatchBroker broker,
            InquiryProperties inquiry,
            Clock matchClock,
            ObjectProvider<ConsultationService> consultations) {
        return new MatchDispatcher(
                broker,
                entry -> consultations.getObject().occupyQueued(entry),
                inquiry,
                matchClock);
    }

    @Bean
    public ApplicationRunner rebuildMatchQueue(@Lazy ConsultationService consultations) {
        return args -> consultations.rebuildMatchQueue();
    }

    @Bean
    @ConditionalOnBean(RedissonClient.class)
    public ApplicationRunner subscribeSlotFreed(RedissonClient redisson, MatchDispatcher dispatcher) {
        return args -> {
            try {
                redisson.getTopic(SLOT_FREED_CHANNEL).addListener(String.class, (channel, body) -> {
                    if (body == null) {
                        return;
                    }
                    int split = body.lastIndexOf('\t');
                    if (split <= 0) {
                        return;
                    }
                    try {
                        dispatcher.onSlotFreed(body.substring(0, split), Integer.parseInt(body.substring(split + 1)));
                    } catch (NumberFormatException ignored) {
                        // 非法放号消息丢掉，下一拍提档或下次放号再试
                    }
                });
            } catch (RuntimeException ignored) {
                // Redis 暂时连不上时仍靠支付入队和 XXL 提档拍
            }
        };
    }
}
