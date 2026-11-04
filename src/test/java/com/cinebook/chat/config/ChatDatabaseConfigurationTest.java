package com.cinebook.chat.config;

import com.cinebook.chat.controller.ChatController;
import com.cinebook.chat.service.ChatEventHub;
import com.cinebook.chat.service.ChatService;
import com.cinebook.feed.service.SocialFeedService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatDatabaseConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner();

    @Test
    void chatDisabledStartsWithoutChatPersistenceBeansOrController() {
        runner.withUserConfiguration(ChatComponents.class)
                .withPropertyValues("cinebook.chat.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ChatController.class);
                    assertThat(context).doesNotHaveBean(ChatService.class);
                    assertThat(context).doesNotHaveBean("chatJdbcTemplate");
                });
    }

    @Test
    void chatEnabledButSocialDatabaseDisabledDoesNotCreateFlywayOrTouchDataSource() {
        runner.withUserConfiguration(DisabledSocialChatComponents.class)
                .withPropertyValues("cinebook.chat.enabled=true", "cinebook.social.database.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("socialFlyway");
                    assertThat(context).doesNotHaveBean("chatFlyway");
                    assertThat(context).doesNotHaveBean(ChatService.class);
                    assertThat(context).doesNotHaveBean(ChatController.class);
                    DataSource dataSource = context.getBean("socialDataSource", DataSource.class);
                    verify(dataSource, never()).getConnection();
                    verify(dataSource, never()).getConnection(anyString(), anyString());
                });
    }

    @Test
    void chatEnabledRegistersQualifiedTemplateAndInjectsItIntoService() {
        runner.withUserConfiguration(EnabledChatComponents.class)
                .withPropertyValues("cinebook.chat.enabled=true", "cinebook.social.database.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("chatFlyway");
                    assertThat(context).hasBean("chatJdbcTemplate");
                    assertThat(context).hasBean("chatTransactionTemplate");
                    assertThat(context).hasSingleBean(ChatController.class);
                    assertThat(context.getBeansOfType(Flyway.class)).hasSize(2);
                    assertThat(context.getBeanFactory().getBeanDefinition("chatFlyway").getDependsOn())
                            .contains("socialFlyway");

                    JdbcTemplate chatJdbc = context.getBean("chatJdbcTemplate", JdbcTemplate.class);
                    ChatService chatService = context.getBean(ChatService.class);
                    assertThat(ReflectionTestUtils.getField(chatService, "jdbc")).isSameAs(chatJdbc);
                    assertThat(chatJdbc.getDataSource()).isSameAs(context.getBean("socialDataSource"));
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({ChatDatabaseConfiguration.class, ChatService.class, ChatController.class, ChatEventHub.class})
    static class ChatComponents { }

    @Configuration(proxyBeanMethods = false)
    @Import(ChatComponents.class)
    static class DisabledSocialChatComponents {
        private final DataSource dataSource = mock(DataSource.class);

        @Bean(name = "socialDataSource")
        DataSource socialDataSource() { return dataSource; }

        @Bean
        SocialFeedService socialFeedService() { return mock(SocialFeedService.class); }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(ChatComponents.class)
    static class EnabledChatComponents {
        @Bean(name = "socialDataSource")
        DataSource socialDataSource() throws Exception {
            DataSource dataSource = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            DatabaseMetaData metadata = mock(DatabaseMetaData.class);
            Statement statement = mock(Statement.class);
            PreparedStatement preparedStatement = mock(PreparedStatement.class);
            ResultSet version = mock(ResultSet.class);
            when(dataSource.getConnection()).thenReturn(connection);
            when(dataSource.getConnection(anyString(), anyString())).thenReturn(connection);
            when(connection.getMetaData()).thenReturn(metadata);
            when(connection.createStatement()).thenReturn(statement);
            when(statement.executeQuery(anyString())).thenReturn(version);
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(version);
            when(version.next()).thenReturn(true);
            when(version.getString(1)).thenReturn("PostgreSQL 15.0");
            when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
            when(metadata.getDatabaseMajorVersion()).thenReturn(15);
            return dataSource;
        }

        @Bean(name = "socialFlyway")
        Flyway socialFlyway() {
            return mock(Flyway.class);
        }

        @Bean
        SocialFeedService socialFeedService() {
            return mock(SocialFeedService.class);
        }

        @Bean
        static BeanFactoryPostProcessor suppressFlywayMigrationForContextTest() {
            return beanFactory -> {
                BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
                AbstractBeanDefinition chatFlyway = (AbstractBeanDefinition) registry.getBeanDefinition("chatFlyway");
                chatFlyway.setInitMethodNames();
            };
        }
    }
}
