package com.moonligh.consult.agent.infrastructure.persistence;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 数据库事务模板。一次 runLocked 包在一个事务里。
 */
@Configuration
public class PersistenceConfiguration {

    /** Spring 事务模板。仓储用它包住一次 runLocked。 */
    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
