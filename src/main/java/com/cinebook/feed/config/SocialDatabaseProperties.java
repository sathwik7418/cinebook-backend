package com.cinebook.feed.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cinebook.social.database")
public record SocialDatabaseProperties(boolean enabled, String url, String username, String password) {
}
