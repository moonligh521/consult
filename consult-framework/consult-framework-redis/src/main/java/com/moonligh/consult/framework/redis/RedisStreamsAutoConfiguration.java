package com.moonligh.consult.framework.redis;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * StringRedisTemplate 仍由 Spring Boot 按 spring.data.redis 创建。这里只补 Stream 模板。
 */
@AutoConfiguration
@ConditionalOnClass(StringRedisTemplate.class)
public class RedisStreamsAutoConfiguration {

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean
    public RedisStreams redisStreams(StringRedisTemplate redis) {
        return new RedisStreams(redis);
    }
}
