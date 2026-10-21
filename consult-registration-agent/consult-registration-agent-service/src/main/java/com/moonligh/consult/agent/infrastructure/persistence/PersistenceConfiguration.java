package com.moonligh.consult.agent.infrastructure.persistence;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 数据库事务模板。没有数据源时不装配。
 */
@Configuration
public class PersistenceConfiguration {

    @Bean
    @ConditionalOnBean(PlatformTransactionManager.class)
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
