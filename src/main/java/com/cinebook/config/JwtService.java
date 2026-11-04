package com.cinebook.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms:86400000}")
    private long expirationMs;

    @Autowired
    private Environment environment;

    @PostConstruct
    void validateConfiguration() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must contain at least 32 bytes.");
        }
        if (expirationMs < 60_000L || expirationMs > 7L * 24 * 60 * 60 * 1000) {
            throw new IllegalStateException("JWT_EXPIRATION_MS must be between one minute and seven days.");
        }
        boolean production = java.util.Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (production && secret.startsWith("cinebook-local-dev-only")) {
            throw new IllegalStateException("A non-development JWT_SECRET is required in production.");
        }
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String subjectEmail) {
        return generateToken(subjectEmail, 0);
    }

    public String generateToken(String subjectEmail, int tokenVersion) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(subjectEmail)
                .claim("ver", tokenVersion)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey())
                .compact();
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public boolean isTokenValid(String token, String expectedEmail) {
        return isTokenValid(token, expectedEmail, 0);
    }

    public boolean isTokenValid(String token, String expectedEmail, int expectedVersion) {
        try {
            String email = extractEmail(token);
            int version = extractClaim(token, claims -> {
                Integer value = claims.get("ver", Integer.class);
                return value == null ? 0 : value;
            });
            return email.equals(expectedEmail) && version == expectedVersion && !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return resolver.apply(claims);
    }
}
