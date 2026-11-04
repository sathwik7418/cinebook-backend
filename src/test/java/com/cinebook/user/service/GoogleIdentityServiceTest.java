package com.cinebook.user.service;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GoogleIdentityServiceTest {
    private static final String CLIENT_ID = "cinebook-web-client.apps.googleusercontent.com";
    private static final String NONCE = "a".repeat(43);
    private KeyPair pair;
    private GoogleJwksKeyProvider keys;
    private GoogleIdentityService service;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        pair = generator.generateKeyPair();
        keys = mock(GoogleJwksKeyProvider.class);
        when(keys.signingKey("test-kid")).thenReturn(pair.getPublic());
        service = new GoogleIdentityService(CLIENT_ID, true, keys);
    }

    @Test
    void verifiesGoogleSignatureAndRequiredIdentityClaims() throws Exception {
        var account = service.verify(token("https://accounts.google.com", CLIENT_ID, Instant.now().plusSeconds(300),
                Instant.now(), NONCE, true, pair), NONCE);
        assertEquals("google-subject-123", account.subject());
        assertEquals("alice@example.com", account.email());
        assertEquals("Alice Example", account.name());
        verify(keys).signingKey("test-kid");
    }

    @Test
    void disabledOrMissingConfigurationReturnsServiceUnavailable() {
        assertStatus(HttpStatus.SERVICE_UNAVAILABLE,
                () -> new GoogleIdentityService(CLIENT_ID, false, keys).verify("bad", NONCE));
        assertStatus(HttpStatus.SERVICE_UNAVAILABLE,
                () -> new GoogleIdentityService("", true, keys).verify("bad", NONCE));
        verifyNoInteractions(keys);
    }

    @Test
    void invalidSignatureIsRejected() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair attacker = generator.generateKeyPair();
        String forged = token("https://accounts.google.com", CLIENT_ID, Instant.now().plusSeconds(300),
                Instant.now(), NONCE, true, attacker);
        assertStatus(HttpStatus.UNAUTHORIZED, () -> service.verify(forged, NONCE));
    }

    @Test
    void issuerAudienceExpirationIssuedAtNonceAndEmailVerificationAreChecked() throws Exception {
        assertStatus(HttpStatus.UNAUTHORIZED, () -> verifyClaim("https://attacker.invalid", CLIENT_ID,
                Instant.now().plusSeconds(300), Instant.now(), NONCE, true));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> verifyClaim("https://accounts.google.com", "another-client",
                Instant.now().plusSeconds(300), Instant.now(), NONCE, true));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> verifyClaim("https://accounts.google.com", CLIENT_ID,
                Instant.now().minusSeconds(1), Instant.now(), NONCE, true));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> verifyClaim("https://accounts.google.com", CLIENT_ID,
                Instant.now().plusSeconds(300), Instant.now().plusSeconds(120), NONCE, true));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> verifyClaim("https://accounts.google.com", CLIENT_ID,
                Instant.now().plusSeconds(300), Instant.now(), "wrong-nonce-123456789012345678901234567890", true));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> verifyClaim("https://accounts.google.com", CLIENT_ID,
                Instant.now().plusSeconds(300), Instant.now(), NONCE, false));
    }

    @Test
    void providerKeyFailureFailsClosedAsServiceUnavailable() throws Exception {
        when(keys.signingKey("test-kid")).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Google could not verify the sign-in right now. Please retry."));
        String signed = token("https://accounts.google.com", CLIENT_ID, Instant.now().plusSeconds(300),
                Instant.now(), NONCE, true, pair);
        assertStatus(HttpStatus.SERVICE_UNAVAILABLE, () -> service.verify(signed, NONCE));
    }

    @Test
    void malformedTokensAndNoncesAreRejectedWithoutRevealingTokenContents() {
        assertStatus(HttpStatus.UNAUTHORIZED, () -> service.verify("not-a-jwt", NONCE));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> service.verify("", NONCE));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> service.verify("x".repeat(12_001), NONCE));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> service.verify("token", "short"));
    }

    private void verifyClaim(String issuer, String audience, Instant expiration, Instant issuedAt,
                             String nonce, boolean emailVerified) throws Exception {
        String jwt = token(issuer, audience, expiration, issuedAt, nonce, emailVerified, pair);
        service.verify(jwt, NONCE);
    }

    private static String token(String issuer, String audience, Instant expiration, Instant issuedAt,
                                String nonce, boolean emailVerified, KeyPair signingKey) {
        return Jwts.builder()
                .header().keyId("test-kid").and()
                .issuer(issuer)
                .subject("google-subject-123")
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiration))
                .claim("aud", audience)
                .claim("nonce", nonce)
                .claim("email", "alice@example.com")
                .claim("email_verified", emailVerified)
                .claim("name", "Alice Example")
                .signWith(signingKey.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private static void assertStatus(HttpStatus expected, org.junit.jupiter.api.function.Executable action) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, action);
        assertEquals(expected, exception.getStatusCode());
        assertFalse(exception.getReason() != null && exception.getReason().contains("google-subject-123"));
    }
}
