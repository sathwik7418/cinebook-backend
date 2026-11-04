package com.cinebook.feed.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SocialDatabaseProperties.class)
public class SocialDatabasePropertiesConfiguration {
}
