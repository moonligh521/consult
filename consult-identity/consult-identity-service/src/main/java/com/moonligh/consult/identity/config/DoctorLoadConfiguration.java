package com.moonligh.consult.identity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.moonligh.consult.identity.domain.load.DoctorLoadPool;
import com.moonligh.consult.identity.infrastructure.load.RedisDoctorLoadPool;

/** 医生在手单数队列。Redis 是可重建的索引，账本仍在 MySQL。 */
@Configuration
public class DoctorLoadConfiguration {

    @Bean
    public DoctorLoadPool doctorLoadPool(StringRedisTemplate redis) {
        return new RedisDoctorLoadPool(redis);
    }
}
