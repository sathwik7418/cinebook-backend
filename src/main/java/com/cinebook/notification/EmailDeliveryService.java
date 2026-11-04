package com.cinebook.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** Optional Resend adapter. An absent key is an explicit unavailable state, never a simulated send. */
@Service
public class EmailDeliveryService {
    @Value("${cinebook.email.api-key:}") private String apiKey;
    @Value("${cinebook.email.from:}") private String from;
    @Value("${cinebook.public-url:http://localhost:5173}") private String publicUrl;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public boolean isConfigured() { return apiKey != null && !apiKey.isBlank() && from != null && !from.isBlank(); }
    public String publicUrl() { return publicUrl.replaceAll("/+$", ""); }

    public boolean send(String to, String subject, String text, String html) {
        if (!isConfigured()) return false;
        try {
            String body = mapper.writeValueAsString(Map.of("from", from, "to", new String[]{to}, "subject", subject,
                    "text", text, "html", html));
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.resend.com/emails"))
                    .timeout(Duration.ofSeconds(8)).header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception ignored) {
            // Never log recipients, tokens, provider response bodies, or credentials.
            return false;
        }
    }
}
