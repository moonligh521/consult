package com.moonligh.consult.agent.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonligh.consult.agent.domain.notice.AssignmentOutbox;
import com.moonligh.consult.agent.infrastructure.notice.MqAssignmentOutbox;
import com.moonligh.consult.framework.mq.ConsultMqSender;

/** 派单通知。没有 Broker 或 Redis 时仍走原来的直接发送。 */
@Configuration
public class DispatchNoticeConfiguration {

    @Bean
    @ConditionalOnBean({ConsultMqSender.class, StringRedisTemplate.class})
    public AssignmentOutbox assignmentOutbox(
            ConsultMqSender sender,
            StringRedisTemplate redis,
            ObjectProvider<ObjectMapper> mapper) {
        return new MqAssignmentOutbox(sender, redis, mapper.getIfAvailable(ObjectMapper::new));
    }
}
