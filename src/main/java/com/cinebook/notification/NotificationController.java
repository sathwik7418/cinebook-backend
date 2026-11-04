package com.cinebook.notification;

import com.cinebook.notification.NotificationService.NotificationRecord;
import com.cinebook.user.model.JsonUserPrincipal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notifications;
    public NotificationController(NotificationService notifications) { this.notifications = notifications; }

    @GetMapping public List<NotificationRecord> list(@AuthenticationPrincipal JsonUserPrincipal user,
            @RequestParam(defaultValue = "30") @Min(1) @Max(100) int limit) {
        return notifications.list(user.getId(), limit);
    }
    @GetMapping("/unread-count") public Map<String, Long> unread(@AuthenticationPrincipal JsonUserPrincipal user) {
        return Map.of("count", notifications.unreadCount(user.getId()));
    }
    @PatchMapping("/{id}/read") public void read(@PathVariable String id, @AuthenticationPrincipal JsonUserPrincipal user) {
        notifications.markRead(user.getId(), id);
    }
    @PatchMapping("/read-all") public void readAll(@AuthenticationPrincipal JsonUserPrincipal user) {
        notifications.markAllRead(user.getId());
    }
}
