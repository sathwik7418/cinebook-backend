package com.cinebook.notification;

import com.cinebook.common.json.JsonFileStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {
    private static final String FILE = "notifications.json";
    private final JsonFileStore store;
    private final Clock clock;
    public NotificationService(JsonFileStore store, Clock clock) { this.store = store; this.clock = clock; }

    public synchronized NotificationRecord create(long userId, String eventKey, String type,
                                                   String title, String message, String link) {
        List<NotificationRecord> all = store.readList(FILE, NotificationRecord.class);
        if (all.stream().anyMatch(item -> item.userId == userId && eventKey.equals(item.eventKey)))
            return all.stream().filter(item -> item.userId == userId && eventKey.equals(item.eventKey)).findFirst().orElseThrow();
        NotificationRecord item = new NotificationRecord();
        item.id = UUID.randomUUID().toString(); item.userId = userId; item.eventKey = eventKey;
        item.type = type; item.title = title; item.message = message;
        item.createdAt = Instant.now(clock).toString();
        item.link = link != null && link.startsWith("/") && !link.startsWith("//") ? link : "/";
        all.add(item); store.writeList(FILE, all); return item;
    }

    public synchronized List<NotificationRecord> list(long userId, int limit) {
        return store.readList(FILE, NotificationRecord.class).stream().filter(item -> item.userId == userId)
                .sorted(Comparator.comparing((NotificationRecord item) -> item.createdAt).reversed())
                .limit(Math.max(1, Math.min(limit, 100))).toList();
    }

    public synchronized long unreadCount(long userId) {
        return store.readList(FILE, NotificationRecord.class).stream()
                .filter(item -> item.userId == userId && item.readAt == null).count();
    }

    public synchronized void markRead(long userId, String id) {
        List<NotificationRecord> all = store.readList(FILE, NotificationRecord.class);
        NotificationRecord item = all.stream().filter(value -> userId == value.userId && id.equals(value.id))
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found."));
        if (item.readAt == null) { item.readAt = Instant.now(clock).toString(); store.writeList(FILE, all); }
    }

    public synchronized void markAllRead(long userId) {
        List<NotificationRecord> all = store.readList(FILE, NotificationRecord.class);
        String now = Instant.now(clock).toString(); boolean changed = false;
        for (NotificationRecord item : all) if (item.userId == userId && item.readAt == null) { item.readAt = now; changed = true; }
        if (changed) store.writeList(FILE, all);
    }

    public static class NotificationRecord {
        public String id; public long userId; public String eventKey; public String type;
        public String title; public String message; public String createdAt; public String readAt; public String link;
    }
}
