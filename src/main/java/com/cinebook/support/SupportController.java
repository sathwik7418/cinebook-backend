package com.cinebook.support;

import com.cinebook.booking.service.BookingService;
import com.cinebook.common.json.JsonFileStore;
import com.cinebook.notification.NotificationService;
import com.cinebook.user.model.JsonUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/support/tickets")
public class SupportController {
    private static final Set<String> CATEGORIES = Set.of("BOOKING", "ACCOUNT", "TICKET", "CANCELLATION", "GENERAL");
    private final JsonFileStore store; private final BookingService bookings; private final NotificationService notifications;
    public SupportController(JsonFileStore store, BookingService bookings, NotificationService notifications) {
        this.store = store; this.bookings = bookings; this.notifications = notifications;
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public SupportTicket create(@Valid @RequestBody CreateRequest request, @AuthenticationPrincipal JsonUserPrincipal user) {
        String category = request.category.trim().toUpperCase();
        if (!CATEGORIES.contains(category)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid support category.");
        if (request.bookingId != null && bookings.get(request.bookingId).userId != user.getId())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found.");
        synchronized (store) {
            List<SupportTicket> all = store.readList("support-tickets.json", SupportTicket.class);
            SupportTicket ticket = new SupportTicket(); ticket.id = UUID.randomUUID().toString(); ticket.userId = user.getId();
            ticket.category = category; ticket.message = request.message.trim(); ticket.bookingId = request.bookingId;
            ticket.status = "OPEN"; ticket.createdAt = Instant.now().toString(); all.add(ticket);
            store.writeList("support-tickets.json", all);
            try { notifications.create(user.getId(), "support:" + ticket.id, "SUPPORT", "Support request received",
                    "Your request was created and is awaiting review.", "/support/requests"); } catch (RuntimeException ignored) { }
            return ticket;
        }
    }

    @GetMapping public List<SupportTicket> list(@AuthenticationPrincipal JsonUserPrincipal user) {
        return store.readList("support-tickets.json", SupportTicket.class).stream()
                .filter(ticket -> ticket.userId == user.getId())
                .sorted(Comparator.comparing((SupportTicket ticket) -> ticket.createdAt).reversed()).toList();
    }

    @GetMapping("/{id}") public SupportTicket get(@PathVariable String id, @AuthenticationPrincipal JsonUserPrincipal user) {
        return store.readList("support-tickets.json", SupportTicket.class).stream()
                .filter(ticket -> id.equals(ticket.id) && ticket.userId == user.getId()).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Support request not found."));
    }

    public static class CreateRequest { @NotBlank @Size(max = 30) public String category; @NotBlank @Size(max = 3000) public String message; public Long bookingId; }
    public static class SupportTicket { public String id; public long userId; public String category; public String message; public Long bookingId; public String status; public String createdAt; }
}
