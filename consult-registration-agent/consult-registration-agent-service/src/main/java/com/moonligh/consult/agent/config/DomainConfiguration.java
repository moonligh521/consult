package com.moonligh.consult.agent.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.moonligh.consult.agent.domain.SlotParser;
import com.moonligh.consult.agent.domain.SymptomExtractor;
import com.moonligh.consult.agent.domain.department.DepartmentCatalog;

/**
 * 挂号领域对象的 Spring 装配。领域类本身不标 @Component。
 */
@Configuration
@EnableConfigurationProperties(InquiryProperties.class)
public class DomainConfiguration {

    /** 从原句抽性别、年龄、病程。 */
    @Bean
    public SlotParser slotParser() {
        return new SlotParser();
    }

    /** 从病情描述抽症状标签。 */
    @Bean
    public SymptomExtractor symptomExtractor(DepartmentCatalog catalog) {
        return new SymptomExtractor(catalog);
    }
}
