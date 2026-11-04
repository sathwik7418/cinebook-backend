package com.cinebook.notification;

import com.cinebook.booking.service.BookingService.Booking;
import com.cinebook.common.json.JsonFileStore;
import com.cinebook.common.json.JsonLoaderService;
import com.cinebook.common.json.JsonLoaderService.AuthUserJson;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class EmailOutboxService {
    private final JsonFileStore store; private final Clock clock; private final JsonLoaderService catalog; private final EmailDeliveryService email;
    public EmailOutboxService(JsonFileStore store, Clock clock, JsonLoaderService catalog, EmailDeliveryService email) { this.store=store;this.clock=clock;this.catalog=catalog;this.email=email; }

    public synchronized String queueBookingEmail(AuthUserJson user, Booking booking, boolean manualRequest) {
        if (!"CONFIRMED".equals(booking.status))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only confirmed demo tickets can be emailed.");
        if (!Boolean.TRUE.equals(user.emailVerified))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Verify your email before requesting a ticket email.");
        if (!manualRequest && user.notificationPreferences != null && Boolean.FALSE.equals(user.notificationPreferences.get("bookingEmail"))) return null;
        List<EmailOutboxItem> all = store.readList("email-outbox.json", EmailOutboxItem.class);
        String key = manualRequest ? "ticket-email:" + booking.id + ":" + UUID.randomUUID() : "booking-confirmed:" + booking.id;
        EmailOutboxItem existing = manualRequest ? null : all.stream().filter(item -> key.equals(item.eventKey)).findFirst().orElse(null);
        if (existing != null) return existing.id;
        String title = catalog.getData("data/cinebook_movies.json", JsonLoaderService.MovieJson.class).stream()
                .filter(item -> item.id == booking.movieId).map(item -> item.title).findFirst()
                .orElse(booking.movieTitle == null ? "Movie" : booking.movieTitle);
        String cinema = catalog.getData("data/cinemas.json", JsonLoaderService.CinemaJson.class).stream()
                .filter(item -> item.id == booking.cinemaId).map(item -> item.name).findFirst().orElse("Cinema");
        String reference = booking.bookingReference == null ? "CB-" + booking.id : booking.bookingReference;
        String link = email.publicUrl() + "/tickets/" + booking.id;
        String text = "CineBook demo booking confirmed.\nMovie: " + title + "\nCinema: " + cinema + "\nScreen: " + booking.screen
                + "\nDate: " + booking.showDate + "\nTime: " + booking.startTime + "\nSeats: " + String.join(", ", booking.seats)
                + "\nReference: " + reference + "\nAmount: INR " + booking.totalAmount
                + "\nDEMO ONLY: not valid for cinema admission. View your ticket after signing in: " + link;
        String html = "<main style='font-family:Arial;color:#eaf0ff;background:#0b1020;padding:24px'><h1 style='color:#62a9ff'>CINEBOOK</h1>"
                + "<p style='padding:8px;border:1px solid #62a9ff'>DEMO BOOKING — not valid for cinema admission</p><h2>" + html(title)
                + "</h2><p>" + html(cinema) + " · " + html(booking.screen) + "</p><p>" + html(booking.showDate) + " · " + html(booking.startTime)
                + "</p><p>Seats: " + html(String.join(", ", booking.seats)) + "</p><p>Reference: " + html(reference) + "</p><p>Total: INR "
                + booking.totalAmount + "</p><a href='" + link + "' style='color:#62a9ff'>View ticket</a></main>";
        EmailOutboxItem event = new EmailOutboxItem(); event.id=UUID.randomUUID().toString();event.eventKey=key;
        event.userId=user.id;event.to=user.email;event.subject="Your CineBook demo booking is confirmed";
        event.text=text;
        event.html=html;event.status="QUEUED";event.attempts=0;event.createdAt=Instant.now(clock).toString();event.nextAttemptAt=event.createdAt;
        all.add(event);store.writeList("email-outbox.json",all);return event.id;
    }

    public synchronized String queueCancellationEmail(AuthUserJson user, Booking booking) {
        if (!Boolean.TRUE.equals(user.emailVerified)
                || user.notificationPreferences != null && Boolean.FALSE.equals(user.notificationPreferences.get("bookingEmail"))) return null;
        List<EmailOutboxItem> all=store.readList("email-outbox.json",EmailOutboxItem.class);
        String key="booking-cancelled:"+booking.id;
        EmailOutboxItem existing=all.stream().filter(item->key.equals(item.eventKey)).findFirst().orElse(null);
        if(existing!=null)return existing.id;
        String reference=booking.bookingReference==null?"CB-"+booking.id:booking.bookingReference;
        EmailOutboxItem item=new EmailOutboxItem();item.id=UUID.randomUUID().toString();item.eventKey=key;item.userId=user.id;
        item.to=user.email;item.subject="Your CineBook demo booking was cancelled";
        item.text="CineBook demo booking cancellation\nReference: "+reference+"\nMovie ID: "+booking.movieId+"\nShow: "+booking.showDate+" "+booking.startTime+"\nSeats: "+String.join(", ",booking.seats)+"\nThis is a demo reservation, not a real cinema ticket.";
        item.html="<main style='font-family:Arial;background:#0b1020;color:#eaf0ff;padding:24px'><h1 style='color:#62a9ff'>CINEBOOK</h1><p>Demo booking cancelled</p><p>Reference: "+html(reference)+"</p><p>Show: "+html(booking.showDate)+" "+html(booking.startTime)+"</p><p>Seats: "+html(String.join(", ",booking.seats))+"</p><p>This is a demo reservation.</p></main>";
        item.status="QUEUED";item.createdAt=Instant.now(clock).toString();item.nextAttemptAt=item.createdAt;all.add(item);store.writeList("email-outbox.json",all);return item.id;
    }

    public synchronized List<EmailOutboxItem> claimReady() {
        List<EmailOutboxItem> all=store.readList("email-outbox.json",EmailOutboxItem.class);boolean changed=false;
        Instant now=Instant.now(clock);
        List<EmailOutboxItem> ready=all.stream().filter(item -> ("QUEUED".equals(item.status)||"FAILED".equals(item.status))
                && item.attempts<4 && item.nextAttemptAt!=null && !Instant.parse(item.nextAttemptAt).isAfter(now)).limit(10).toList();
        for(EmailOutboxItem item:ready){item.status="SENDING";item.attempts++;changed=true;}
        if(changed)store.writeList("email-outbox.json",all);
        return ready;
    }

    public synchronized EmailOutboxItem get(String id) { return store.readList("email-outbox.json",EmailOutboxItem.class).stream()
            .filter(item->id.equals(item.id)).findFirst().orElse(null); }

    public synchronized void finish(String id, boolean accepted) {
        List<EmailOutboxItem> all=store.readList("email-outbox.json",EmailOutboxItem.class);
        all.stream().filter(item->id.equals(item.id)).findFirst().ifPresent(item->{
            item.status=accepted?"ACCEPTED":"FAILED";item.updatedAt=Instant.now(clock).toString();
            item.lastError=accepted?null:"Provider unavailable or rejected the request.";
            long delaySeconds=Math.min(3600, 30L << Math.min(item.attempts,7));
            item.nextAttemptAt=Instant.now(clock).plusSeconds(delaySeconds).toString();
        });store.writeList("email-outbox.json",all);
    }
    private static String html(String value){return value==null?"-":value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
    public static class EmailOutboxItem {public String id;public String eventKey;public long userId;public String to;public String subject;public String text;public String html;public String status;public int attempts;public String createdAt;public String updatedAt;public String nextAttemptAt;public String lastError;}
}
