package com.moonligh.consult.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.moonligh.consult.agent.domain.DepartmentRouter;
import com.moonligh.consult.agent.domain.SlotParser;
import com.moonligh.consult.agent.domain.SymptomExtractor;

/**
 * 挂号领域对象的 Spring 装配。领域类本身不标 @Component。
 */
@Configuration
public class DomainConfiguration {

    /** 从原句抽性别、年龄、病程。 */
    @Bean
    public SlotParser slotParser() {
        return new SlotParser();
    }

    /** 从病情描述抽症状标签。 */
    @Bean
    public SymptomExtractor symptomExtractor() {
        return new SymptomExtractor();
    }

    /** 按关键词推荐科室。 */
    @Bean
    public DepartmentRouter departmentRouter() {
        return new DepartmentRouter();
    }
}
