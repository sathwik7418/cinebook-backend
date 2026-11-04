package com.cinebook.config;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    @Value("${cors.allowed-methods}")
    private String allowedMethods;

    @Value("${cors.allowed-headers}")
    private String allowedHeaders;

    @Value("${cors.exposed-headers}")
    private String exposedHeaders;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        AuthenticationEntryPoint unauthorized = (request, response, exception) -> {
            if (request.getRequestURI().startsWith(request.getContextPath() + "/api/chat/")) {
                String authorization = request.getHeader("Authorization");
                log.warn("chat_security_denied method={} path={} authorization_present={} exception={}",
                        request.getMethod(), request.getRequestURI(), authorization != null,
                        exception.getClass().getSimpleName());
            }
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
        };
        AccessDeniedHandler forbidden = new AccessDeniedHandlerImpl();

        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS
                )
            )
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint(unauthorized)
                .accessDeniedHandler(forbidden)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/google/link").authenticated()
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers(
                    "/api/movies/**",
                    "/api/cinetickets/**",
                    "/api/cinemas/**",
                    "/api/theaters/**",
                    "/api/shows/**",
                    "/api/seats/**"
                ).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/bookings/shows/*/seats").permitAll()
                .requestMatchers("/api/users/*/bookings").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/users", "/api/users/*").denyAll()
                .requestMatchers("/api/bookings/**").authenticated()
                .requestMatchers("/api/profile/**").authenticated()
                .requestMatchers("/images/**").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(
                jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration =
                new CorsConfiguration();

        configuration.setAllowedOrigins(
                Arrays.stream(allowedOrigins.split(","))
                        .map(String::trim)
                        .toList()
        );

        configuration.setAllowedMethods(
                Arrays.stream(allowedMethods.split(","))
                        .map(String::trim)
                        .toList()
        );

        configuration.setAllowedHeaders(Arrays.stream(allowedHeaders.split(","))
                .map(String::trim).toList());

        configuration.setExposedHeaders(
                Arrays.stream(exposedHeaders.split(","))
                        .map(String::trim)
                        .toList()
        );

        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
