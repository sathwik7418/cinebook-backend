package com.cinebook.feed.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SocialDatabaseConfigurationTest {

    @Test
    void socialConnectionsUseTheSchemaWhereFlywayInstallsCineFeed() {
        SocialDatabaseConfiguration configuration = new SocialDatabaseConfiguration();
        SocialDatabaseProperties properties = new SocialDatabaseProperties(
                true,
                "jdbc:postgresql://localhost:5432/cinebook?currentSchema=public",
                "test-user",
                "test-password");

        try (HikariDataSource dataSource = configuration.socialDataSource(properties)) {
            assertEquals("cinefeed", dataSource.getSchema());
        }
    }
}
