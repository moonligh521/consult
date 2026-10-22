package com.moonligh.consult.agent.infrastructure.persistence;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 事务模板。数据源和 JdbcTemplate 由 Spring Boot 根据 application.yml 自动创建。
 */
@Configuration
public class PersistenceConfiguration {

    /** 一次 runLocked 包在一个事务里。 */
    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
