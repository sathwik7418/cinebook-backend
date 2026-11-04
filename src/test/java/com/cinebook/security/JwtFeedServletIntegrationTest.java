package com.cinebook.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "cinebook.social.database.enabled=false",
        "cinebook.storage.directory=target/jwt-feed-servlet-test-data",
        "jwt.secret=test-only-secret-key-with-more-than-32-bytes",
        "jwt.expiration-ms=86400000"
})
@Import(JwtFeedServletIntegrationTest.FeedProbeController.class)
class JwtFeedServletIntegrationTest {
    @LocalServerPort private int port;
    @Autowired private TestRestTemplate client;

    private String bearer;

    @BeforeEach
    void setUp() throws Exception {
        String username = "servlet_test_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String email = "servlet-test-" + java.util.UUID.randomUUID().toString().replace("-", "") + "@example.invalid";
        byte[] random = new byte[32];
        java.security.SecureRandom.getInstanceStrong().nextBytes(random);
        String password = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(random) + "!Q7";

        HttpHeaders registrationHeaders = new HttpHeaders();
        registrationHeaders.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        ResponseEntity<Map> registration = client.postForEntity(url("/api/auth/register"),
                new HttpEntity<>(Map.of("username", username, "email", email,
                        "password", password, "name", "Servlet Filter Test"), registrationHeaders), Map.class);
        assertEquals(200, registration.getStatusCode().value());
        bearer = "Bearer " + registration.getBody().get("token");
    }

    @Test
    void sameJwtSurvivesServletAndSpringSecurityFilterRegistrations() {
        assertEquals(200, get("/api/auth/me").getStatusCode().value());
        ResponseEntity<Map> feedMe = get("/api/feed/me");
        assertEquals(200, feedMe.getStatusCode().value());
        assertEquals("com.cinebook.user.model.JsonUserPrincipal", feedMe.getBody().get("principalType"));
        assertEquals(200, get("/api/feed").getStatusCode().value());
    }

    @Test
    void absentAndInvalidTokensAreRejectedByTheEmbeddedServer() {
        ResponseEntity<String> absent = client.getForEntity(url("/api/feed/me"), String.class);
        assertEquals(401, absent.getStatusCode().value());

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer invalid-token");
        ResponseEntity<String> invalid = client.exchange(url("/api/feed/me"), HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertEquals(401, invalid.getStatusCode().value());
    }

    private ResponseEntity<Map> get(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, bearer);
        return client.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @RestController
    @RequestMapping("/api/feed")
    static class FeedProbeController {
        @GetMapping("/me")
        Map<String, Object> me(@org.springframework.security.core.annotation.AuthenticationPrincipal
                               com.cinebook.user.model.JsonUserPrincipal user) {
            return Map.of("principalType", user.getClass().getName(), "id", user.getId());
        }

        @GetMapping
        Map<String, Object> feed(@org.springframework.security.core.annotation.AuthenticationPrincipal
                                 com.cinebook.user.model.JsonUserPrincipal user) {
            return Map.of("authenticated", user != null);
        }
    }
}
