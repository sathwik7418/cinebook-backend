package com.cinebook.cinetickets;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Configuration
@EnableConfigurationProperties(OmdbProperties.class)
public class CineTicketsConfiguration {
    @Bean
    RestTemplate omdbRestTemplate(OmdbProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(Math.max(500, properties.getConnectTimeout())));
        factory.setReadTimeout(Duration.ofMillis(Math.max(500, properties.getReadTimeout())));
        return new RestTemplate(factory);
    }

    @Bean(destroyMethod = "shutdown")
    ExecutorService cineTicketsExecutor() {
        return Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "cinetickets-omdb");
            thread.setDaemon(true);
            return thread;
        });
    }
}
