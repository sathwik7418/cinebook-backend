package com.cinebook.user.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.PublicKey;
import java.time.Instant;
import java.util.Collection;

/** Verifies Google GIS ID tokens with JJWT and Google's rotating signing keys. */
@Service
public class GoogleIdentityService {
    private static final String GOOGLE_ISSUER = "https://accounts.google.com";
    private final String clientId;
    private final boolean enabled;
    private final GoogleJwksKeyProvider keyProvider;

    public GoogleIdentityService(@Value("${google.client-id:}") String clientId,
                                 @Value("${google.enabled:false}") boolean enabled,
                                 GoogleJwksKeyProvider keyProvider) {
        this.clientId = clientId == null ? "" : clientId.trim();
        this.enabled = enabled;
        this.keyProvider = keyProvider;
    }

    public GoogleAccount verify(String rawToken, String expectedNonce) {
        if (!enabled || clientId.isBlank() || keyProvider == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Google sign-in is not available in this environment.");
        }
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 12_000
                || expectedNonce == null || expectedNonce.length() < 32 || expectedNonce.length() > 256) {
            throw invalidCredential();
        }

        try {
            String[] parts = rawToken.split("\\.");
            if (parts.length != 3) throw invalidCredential();
            var header = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                    java.util.Base64.getUrlDecoder().decode(parts[0]));
            if (!"RS256".equals(header.path("alg").asText())) throw invalidCredential();
            String keyId = header.path("kid").asText();
            if (keyId.isBlank()) throw invalidCredential();
            PublicKey publicKey = keyProvider.signingKey(keyId);
            Claims claims = Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(rawToken).getPayload();

            Object audience = claims.get("aud");
            boolean audienceMatches = clientId.equals(audience)
                    || audience instanceof Collection<?> audiences && audiences.contains(clientId);
            if (audience instanceof Collection<?> audiences && audiences.size() > 1
                    && !clientId.equals(claims.get("azp", String.class))) throw invalidCredential();

            String issuer = claims.getIssuer();
            Instant expiration = claims.getExpiration() == null ? null : claims.getExpiration().toInstant();
            Instant issuedAt = claims.getIssuedAt() == null ? null : claims.getIssuedAt().toInstant();
            Instant now = Instant.now();
            String subject = claims.getSubject();
            String email = claims.get("email", String.class);
            Object emailVerified = claims.get("email_verified");
            Object nonce = claims.get("nonce");
            String name = claims.get("name", String.class);
            if (!(GOOGLE_ISSUER.equals(issuer) || "accounts.google.com".equals(issuer))
                    || !audienceMatches || expiration == null || !expiration.isAfter(now)
                    || issuedAt == null || issuedAt.isAfter(now.plusSeconds(60))
                    || !expectedNonce.equals(nonce)
                    || !Boolean.TRUE.equals(emailVerified) && !"true".equalsIgnoreCase(String.valueOf(emailVerified))
                    || subject == null || subject.isBlank() || email == null || email.isBlank()) {
                throw invalidCredential();
            }
            return new GoogleAccount(subject, email, name);
        } catch (ResponseStatusException status) {
            throw status;
        } catch (Exception invalidTokenOrSignature) {
            throw invalidCredential();
        }
    }

    private static ResponseStatusException invalidCredential() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Google sign-in could not be verified. Please try again.");
    }

    public record GoogleAccount(String subject, String email, String name) { }
}
