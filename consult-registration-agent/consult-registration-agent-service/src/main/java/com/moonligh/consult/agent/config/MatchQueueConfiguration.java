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
import org.springframework.data.redis.core.StringRedisTemplate;

import com.moonligh.consult.agent.domain.MatchBroker;
import com.moonligh.consult.agent.domain.MemoryMatchBroker;
import com.moonligh.consult.agent.infrastructure.match.RedissonMatchBroker;
import com.moonligh.consult.agent.infrastructure.match.SlotFreedStreamConsumer;
import com.moonligh.consult.agent.service.ConsultationService;
import com.moonligh.consult.agent.service.MatchDispatcher;

/**
 * 派医生队：Redis ZSET 按支付时间排队；没有 Redisson 时用内存队。
 * 放号走 Redis Stream；对账和提档走 XXL。
 */
@Configuration
@ConditionalOnProperty(prefix = "inquiry", name = "match-queue-enabled", havingValue = "true", matchIfMissing = true)
public class MatchQueueConfiguration {

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
    @ConditionalOnBean(StringRedisTemplate.class)
    public SlotFreedStreamConsumer slotFreedStreamConsumer(
            StringRedisTemplate redis,
            MatchDispatcher dispatcher) {
        return new SlotFreedStreamConsumer(redis, dispatcher);
    }
}
