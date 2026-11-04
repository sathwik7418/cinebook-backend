package com.cinebook.common.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/** Small bounded per-process limiter appropriate for the single-instance demo. */
@Component
public class AuthRateLimiter {
    private static final int MAX_KEYS = 10_000;
    private final Map<String, Window> windows = new HashMap<>();
    private final Clock clock;

    public AuthRateLimiter(Clock clock) { this.clock = clock; }

    public synchronized void check(String operation, String clientKey, int limit, long windowMs) {
        long now = clock.millis();
        String key = operation + ":" + (clientKey == null ? "unknown" : clientKey);
        Window current = windows.get(key);
        if (current == null || now - current.startedAt >= windowMs) {
            if (windows.size() >= MAX_KEYS) {
                windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt >= windowMs);
                if (windows.size() >= MAX_KEYS) windows.clear();
            }
            current = new Window(now, 0);
            windows.put(key, current);
        }
        if (current.count >= limit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many attempts. Please wait and try again.");
        }
        current.count++;
    }

    private static final class Window {
        private final long startedAt;
        private int count;
        private Window(long startedAt, int count) { this.startedAt = startedAt; this.count = count; }
    }
}
