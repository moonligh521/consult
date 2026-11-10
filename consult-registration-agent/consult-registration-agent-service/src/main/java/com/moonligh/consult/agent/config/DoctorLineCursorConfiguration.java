package com.moonligh.consult.agent.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.moonligh.consult.agent.domain.DoctorLineCursor;
import com.moonligh.consult.agent.infrastructure.line.RedisDoctorLineCursor;

/** 有 Redis 时，医生回复的序号放在那里，轮询没变就不读订单。 */
@Configuration
public class DoctorLineCursorConfiguration {

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    public DoctorLineCursor doctorLineCursor(StringRedisTemplate redis) {
        return new RedisDoctorLineCursor(redis);
    }
}
