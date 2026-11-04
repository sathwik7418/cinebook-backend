package com.cinebook.user.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fetches and caches only Google's public RS256 signing keys. */
@Service
public class GoogleJwksKeyProvider {
    private static final URI JWKS_URI = URI.create("https://www.googleapis.com/oauth2/v3/certs");
    private static final Pattern MAX_AGE = Pattern.compile("(?:^|,)\\s*max-age=(\\d+)", Pattern.CASE_INSENSITIVE);
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private final Map<String, PublicKey> keys = new ConcurrentHashMap<>();
    private volatile Instant expiry = Instant.EPOCH;

    public PublicKey signingKey(String keyId) {
        if (keyId == null || keyId.isBlank() || keyId.length() > 256) throw invalidKey();
        if (Instant.now().isBefore(expiry) && keys.containsKey(keyId)) return keys.get(keyId);
        synchronized (this) {
            if (Instant.now().isBefore(expiry) && keys.containsKey(keyId)) return keys.get(keyId);
            refresh();
            PublicKey key = keys.get(keyId);
            if (key == null) throw invalidKey();
            return key;
        }
    }

    private void refresh() {
        try {
            HttpRequest request = HttpRequest.newBuilder(JWKS_URI).timeout(Duration.ofSeconds(6)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw unavailable();
            JsonNode root = mapper.readTree(response.body());
            Map<String, PublicKey> refreshed = new ConcurrentHashMap<>();
            for (JsonNode item : root.path("keys")) {
                if (!"RSA".equals(item.path("kty").asText())) continue;
                if (item.hasNonNull("alg") && !"RS256".equals(item.path("alg").asText())) continue;
                if (item.hasNonNull("use") && !"sig".equals(item.path("use").asText())) continue;
                String kid = item.path("kid").asText();
                String modulus = item.path("n").asText();
                String exponent = item.path("e").asText();
                if (kid.isBlank() || modulus.isBlank() || exponent.isBlank()) continue;
                BigInteger n = new BigInteger(1, Base64.getUrlDecoder().decode(modulus));
                BigInteger e = new BigInteger(1, Base64.getUrlDecoder().decode(exponent));
                PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, e));
                refreshed.put(kid, key);
            }
            if (refreshed.isEmpty()) throw unavailable();
            keys.clear();
            keys.putAll(refreshed);
            expiry = Instant.now().plusSeconds(cacheSeconds(response.headers().firstValue("Cache-Control").orElse("")));
        } catch (ResponseStatusException failure) {
            throw failure;
        } catch (Exception providerFailure) {
            throw unavailable();
        }
    }

    private long cacheSeconds(String header) {
        Matcher matcher = MAX_AGE.matcher(header);
        if (!matcher.find()) return 1800;
        try { return Math.max(60, Math.min(3600, Long.parseLong(matcher.group(1)))); }
        catch (NumberFormatException ignored) { return 1800; }
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Google could not verify the sign-in right now. Please retry.");
    }

    private static ResponseStatusException invalidKey() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Google sign-in could not be verified. Please try again.");
    }
}
