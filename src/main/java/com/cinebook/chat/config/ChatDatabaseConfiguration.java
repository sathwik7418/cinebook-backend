package com.cinebook.chat.config;

import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

/** CineChat uses an isolated schema and independent Flyway history on the existing social datasource. */
@Configuration
@Conditional(CineChatPersistenceEnabledCondition.class)
public class ChatDatabaseConfiguration {
    @Bean(name = "chatFlyway", initMethod = "migrate")
    @DependsOn("socialFlyway")
    Flyway chatFlyway(@Qualifier("socialDataSource") DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/chat")
                .schemas("cinechat")
                .defaultSchema("cinechat")
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .load();
    }

    @Bean(name = "chatJdbcTemplate")
    @DependsOn("chatFlyway")
    JdbcTemplate chatJdbcTemplate(@Qualifier("socialDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "chatTransactionManager")
    @DependsOn("chatFlyway")
    PlatformTransactionManager chatTransactionManager(@Qualifier("socialDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean(name = "chatTransactionTemplate")
    TransactionTemplate chatTransactionTemplate(
            @Qualifier("chatTransactionManager") PlatformTransactionManager manager) {
        return new TransactionTemplate(manager);
    }
}
