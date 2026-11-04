package com.cinebook.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    private JwtService configured(String profile, String secret) {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secret", secret);
        ReflectionTestUtils.setField(service, "expirationMs", 86_400_000L);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        ReflectionTestUtils.setField(service, "environment", environment);
        return service;
    }

    @Test void tokenVersionAllowsPasswordResetToRevokeExistingTokens() {
        JwtService service = configured("dev", "a-local-test-secret-long-enough-for-hmac-sha256");
        String token = service.generateToken("user@example.test", 0);
        assertTrue(service.isTokenValid(token, "user@example.test", 0));
        assertFalse(service.isTokenValid(token, "user@example.test", 1));
        assertFalse(service.isTokenValid(token, "other@example.test", 0));
    }

    @Test void productionRejectsDevelopmentSecretAndShortSecrets() {
        assertThrows(IllegalStateException.class, () -> configured("prod", "cinebook-local-dev-only-secret-key-rotate-before-deploy").validateConfiguration());
        assertThrows(IllegalStateException.class, () -> configured("dev", "too-short").validateConfiguration());
        assertDoesNotThrow(() -> configured("prod", "unique-production-secret-with-at-least-32-bytes").validateConfiguration());
    }
}
