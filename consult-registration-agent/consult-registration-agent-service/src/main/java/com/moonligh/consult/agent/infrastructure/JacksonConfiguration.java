package com.moonligh.consult.agent.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Jackson 2 的 ObjectMapper。Boot 4 默认自动配置的是 Jackson 3，卡片 JSON 仍用 2。
 */
@Configuration
public class JacksonConfiguration {

    /** 给 ConsultationMapper 写 card_json、审计 JSON。 */
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
