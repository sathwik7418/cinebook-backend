package com.cinebook.ticket;

import com.cinebook.booking.service.BookingService;
import com.cinebook.booking.service.BookingService.Booking;
import com.cinebook.common.json.JsonLoaderService;
import com.cinebook.user.model.JsonUserPrincipal;
import com.cinebook.notification.EmailOutboxService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.List;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {
    private final BookingService bookings; private final JsonLoaderService catalog; private final TicketPdfService pdf; private final EmailOutboxService emailOutbox;
    public TicketController(BookingService bookings, JsonLoaderService catalog, TicketPdfService pdf, EmailOutboxService emailOutbox) {
        this.bookings = bookings; this.catalog = catalog; this.pdf = pdf; this.emailOutbox=emailOutbox;
    }
    @GetMapping public List<Booking> list(@AuthenticationPrincipal JsonUserPrincipal user) { return bookings.forUser(user.getId()); }
    @GetMapping("/{id}") public Booking get(@PathVariable long id, @AuthenticationPrincipal JsonUserPrincipal user) {
        return owned(id, user);
    }
    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> download(@PathVariable long id, @AuthenticationPrincipal JsonUserPrincipal user) {
        Booking booking = owned(id, user);
        String movie = catalog.getData("data/cinebook_movies.json", JsonLoaderService.MovieJson.class).stream()
                .filter(item -> item.id == booking.movieId).map(item -> item.title).findFirst()
                .orElse(booking.movieTitle == null ? "Movie" : booking.movieTitle);
        String cinema = catalog.getData("data/cinemas.json", JsonLoaderService.CinemaJson.class).stream()
                .filter(item -> item.id == booking.cinemaId).map(item -> item.name).findFirst().orElse("Cinema");
        String ref = booking.bookingReference == null ? "CB-" + booking.id : booking.bookingReference;
        byte[] bytes = pdf.create(booking, movie, cinema);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(ref + ".pdf").build().toString())
                .body(bytes);
    }
    @PostMapping("/{id}/email")
    public ResponseEntity<Void> email(@PathVariable long id, @AuthenticationPrincipal JsonUserPrincipal user) {
        Booking booking = owned(id, user);
        String event = emailOutbox.queueBookingEmail(user.getUser(), booking, true);
        return ResponseEntity.accepted().header("X-Email-Event", event).build();
    }
    private Booking owned(long id, JsonUserPrincipal user) {
        Booking booking = bookings.get(id);
        if (booking.userId != user.getId()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found.");
        return booking;
    }
}
