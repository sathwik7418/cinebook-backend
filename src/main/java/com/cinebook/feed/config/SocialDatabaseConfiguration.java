package com.cinebook.feed.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.DependsOn;

/** Isolated JDBC persistence for CineFeed. The legacy JSON-backed modules keep their existing configuration. */
@Configuration
@ConditionalOnProperty(prefix = "cinebook.social.database", name = "enabled", havingValue = "true")
public class SocialDatabaseConfiguration {
    @Bean(name = "socialDataSource")
    HikariDataSource socialDataSource(SocialDatabaseProperties properties) {
        HikariDataSource source = new HikariDataSource();
        source.setPoolName("cinefeed-postgres");
        source.setDriverClassName("org.postgresql.Driver");
        source.setJdbcUrl(properties.url());
        source.setUsername(properties.username());
        source.setPassword(properties.password());
        source.setSchema("cinefeed");
        source.setMaximumPoolSize(8);
        source.setMinimumIdle(1);
        source.setConnectionTimeout(5000);
        return source;
    }

    @Bean(name = "socialFlyway", initMethod = "migrate")
    Flyway socialFlyway(DataSource socialDataSource) {
        return Flyway.configure()
                .dataSource(socialDataSource)
                .locations("classpath:db/migration/social")
                .schemas("cinefeed")
                .defaultSchema("cinefeed")
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .load();
    }

    @Bean(name = "socialJdbcTemplate")
    @DependsOn("socialFlyway")
    JdbcTemplate socialJdbcTemplate(DataSource socialDataSource) {
        return new JdbcTemplate(socialDataSource);
    }

    @Bean(name = "socialTransactionManager")
    @DependsOn("socialFlyway")
    PlatformTransactionManager socialTransactionManager(DataSource socialDataSource) {
        return new DataSourceTransactionManager(socialDataSource);
    }

    @Bean(name = "socialTransactionTemplate")
    TransactionTemplate socialTransactionTemplate(PlatformTransactionManager socialTransactionManager) {
        return new TransactionTemplate(socialTransactionManager);
    }
}
