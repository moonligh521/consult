package com.moonligh.consult.framework.mq;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 必须排在 RocketMQ 自动配置之后，否则 {@code RocketMQTemplate} 还没注册，Sender 会一直缺失。
 */
@AutoConfiguration(afterName = "org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration")
@ConditionalOnClass(RocketMQTemplate.class)
@EnableConfigurationProperties(ConsultMqProperties.class)
@ConditionalOnProperty(prefix = "consult.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ConsultMqAutoConfiguration {

    @Bean
    @ConditionalOnBean(RocketMQTemplate.class)
    @ConditionalOnMissingBean
    public ConsultMqSender consultMqSender(RocketMQTemplate rocketMQTemplate, ObjectProvider<ObjectMapper> mapper) {
        return new ConsultMqSender(rocketMQTemplate, mapper.getIfAvailable(ObjectMapper::new));
    }
}
