package com.cinebook.booking.controller;

import com.cinebook.booking.service.BookingService;
import com.cinebook.booking.service.BookingService.Booking;
import com.cinebook.booking.service.BookingService.SnackSelection;
import com.cinebook.notification.NotificationService;
import com.cinebook.notification.EmailOutboxService;
import com.cinebook.user.model.JsonUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
public class BookingController {
    private final BookingService bookings;

    private final NotificationService notifications;
    private final EmailOutboxService emailOutbox;

    public BookingController(BookingService bookings, NotificationService notifications, EmailOutboxService emailOutbox) {
        this.bookings = bookings; this.notifications = notifications; this.emailOutbox = emailOutbox;
    }

    @GetMapping("/api/bookings/shows/{showId}/seats")
    public List<String> reservedSeats(@PathVariable long showId) { return bookings.reservedSeats(showId); }

    @PostMapping("/api/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public Booking create(@Valid @RequestBody CreateBookingRequest request,
                          @AuthenticationPrincipal JsonUserPrincipal user) {
        Booking booking = bookings.create(user.getId(), request.showId, request.seats, request.snacks);
        try { notifications.create(user.getId(), "booking-confirmed:" + booking.id, "BOOKING",
                "Demo booking confirmed", "Your demo ticket is ready to view.", "/tickets/" + booking.id); }
        catch (RuntimeException ignored) { /* Booking success is independent from notification delivery. */ }
        try { emailOutbox.queueBookingEmail(user.getUser(), booking, false); }
        catch (RuntimeException ignored) { /* Persisted booking remains successful if email cannot be queued. */ }
        return booking;
    }

    @GetMapping("/api/bookings/{id}")
    public Booking get(@PathVariable long id, @AuthenticationPrincipal JsonUserPrincipal user) {
        Booking booking = bookings.get(id);
        requireOwner(booking, user);
        return booking;
    }

    @GetMapping("/api/users/{userId}/bookings")
    public List<Booking> forUser(@PathVariable long userId, @AuthenticationPrincipal JsonUserPrincipal user) {
        if (userId != user.getId()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot access another user's bookings.");
        return bookings.forUser(userId);
    }

    @DeleteMapping("/api/bookings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable long id, @AuthenticationPrincipal JsonUserPrincipal user) {
        bookings.cancel(id, user.getId());
        try { notifications.create(user.getId(), "booking-cancelled:" + id, "BOOKING",
                "Booking cancelled", "Your demo reservation was cancelled.", "/bookings"); }
        catch (RuntimeException ignored) { /* Booking state remains authoritative. */ }
        try { emailOutbox.queueCancellationEmail(user.getUser(), bookings.get(id)); }
        catch (RuntimeException ignored) { /* Cancellation does not depend on email delivery. */ }
    }

    private void requireOwner(Booking booking, JsonUserPrincipal user) {
        if (user == null || booking.userId != user.getId())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot access another user's booking.");
    }

    public static class CreateBookingRequest {
        @NotNull public Long showId;
        @NotEmpty @Size(max = 6) public List<String> seats;
        @Valid @Size(max = 8) public List<SnackSelection> snacks = List.of();
    }
}
