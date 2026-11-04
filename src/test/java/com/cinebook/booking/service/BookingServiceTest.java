package com.cinebook.booking.service;

import com.cinebook.common.json.JsonLoaderService.ShowJson;
import com.cinebook.show.service.DynamicShowtimeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookingServiceTest {
    @TempDir Path temp;

    private BookingService createService(DynamicShowtimeService shows) {
        return new BookingService(temp.toString(), shows,
                Clock.fixed(Instant.parse("2026-11-01T05:00:00Z"), ZoneId.of("Asia/Kolkata")));
    }

    @Test
    void reservationsPersistAcrossServiceRestartAndRejectDoubleBooking() {
        DynamicShowtimeService shows = mock(DynamicShowtimeService.class);
        ShowJson show = new ShowJson();
        show.id = 12345;
        show.price = 200;
        show.date = "2026-11-02";
        show.startTime = "09:30:00";
        when(shows.getShow(12345)).thenReturn(show);

        BookingService firstRun = createService(shows);
        var booking = firstRun.create(41, show.id, List.of("A1", "G2"));
        assertEquals(List.of("A1", "G2"), booking.seats);
        assertEquals(142, firstRun.availableSeats(show.id));
        assertEquals(show.movieId, booking.movieId);
        assertEquals(show.cinemaId, booking.cinemaId);
        assertEquals(show.date, booking.showDate);
        assertEquals(show.startTime, booking.startTime);

        BookingService afterRestart = createService(shows);
        assertEquals(List.of("A1", "G2"), afterRestart.reservedSeats(show.id));
        assertThrows(ResponseStatusException.class, () -> afterRestart.create(42, show.id, List.of("A1")));
    }

    @Test
    void snackPricesAndSeatPricesAreCalculatedByBackendAndSavedAsShowSnapshot() {
        DynamicShowtimeService shows = mock(DynamicShowtimeService.class);
        ShowJson show = futureShow(12345);
        show.movieId = 7;
        show.cinemaId = 3;
        show.screen = "Screen 2";
        when(shows.getShow(show.id)).thenReturn(show);

        BookingService service = createService(shows);
        BookingService.SnackSelection popcorn = new BookingService.SnackSelection();
        popcorn.itemId = "salted-popcorn";
        popcorn.quantity = 2;
        var booking = service.create(41, show.id, List.of("A1", "G2"), List.of(popcorn));

        assertEquals("Screen 2", booking.screen);
        assertEquals(500, booking.snackAmount);
        assertEquals(1023, booking.totalAmount); // ₹480 seats + ₹43 fee + ₹500 snacks
        assertEquals(1, booking.snacks.size());
        assertEquals(250, booking.snacks.get(0).unitPrice);
        assertEquals(500, booking.snacks.get(0).subtotal);
    }

    @Test
    void invalidSnackIdAndQuantityAreRejected() {
        DynamicShowtimeService shows = mock(DynamicShowtimeService.class);
        ShowJson show = futureShow(12345);
        when(shows.getShow(show.id)).thenReturn(show);
        BookingService service = createService(shows);

        BookingService.SnackSelection invalidItem = new BookingService.SnackSelection();
        invalidItem.itemId = "client-controlled-price";
        invalidItem.quantity = 1;
        assertThrows(ResponseStatusException.class, () -> service.create(41, show.id, List.of("A1"), List.of(invalidItem)));

        BookingService.SnackSelection invalidQuantity = new BookingService.SnackSelection();
        invalidQuantity.itemId = "salted-popcorn";
        invalidQuantity.quantity = 13;
        assertThrows(ResponseStatusException.class, () -> service.create(41, show.id, List.of("A1"), List.of(invalidQuantity)));
    }

    @Test
    void expiredShowIsRejectedAtBookingTime() {
        DynamicShowtimeService shows = mock(DynamicShowtimeService.class);
        ShowJson show = new ShowJson();
        show.id = 987;
        show.price = 200;
        show.date = "2026-11-01";
        show.startTime = "09:30:00";
        when(shows.getShow(987)).thenReturn(show);
        assertThrows(ResponseStatusException.class, () -> createService(shows).create(41, 987, List.of("A1")));
    }

    @Test
    void simultaneousRequestsCannotClaimTheSameSeat() throws Exception {
        DynamicShowtimeService shows = mock(DynamicShowtimeService.class);
        ShowJson show = futureShow(12345);
        when(shows.getShow(show.id)).thenReturn(show);
        BookingService service = createService(shows);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger created = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> attemptReservation(service, 1, show.id, ready, start, created, rejected));
            var second = executor.submit(() -> attemptReservation(service, 2, show.id, ready, start, created, rejected));
            ready.await();
            start.countDown();
            first.get();
            second.get();
            assertEquals(1, created.get());
            assertEquals(1, rejected.get());
            assertEquals(List.of("B4"), service.reservedSeats(show.id));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void bookingIdsAreUniqueHistorySurvivesWindowExpiryAndCancellationReleasesSeats() {
        DynamicShowtimeService shows = mock(DynamicShowtimeService.class);
        ShowJson show = futureShow(12345);
        when(shows.getShow(show.id)).thenReturn(show);
        BookingService service = createService(shows);
        var first = service.create(41, show.id, List.of("A1"));
        var second = service.create(41, show.id, List.of("A2"));
        assertNotEquals(first.id, second.id);
        service.cancel(first.id, 41);
        assertEquals(List.of("A2"), service.reservedSeats(show.id));
        assertEquals(143, service.availableSeats(show.id));

        when(shows.getShow(show.id)).thenReturn(null); // the show has rolled out of the booking window
        BookingService restarted = createService(shows);
        assertEquals("CANCELLED", restarted.get(first.id).status);
        assertEquals("CONFIRMED", restarted.get(second.id).status);
        assertEquals(List.of("A2"), restarted.reservedSeats(show.id));
    }

    private ShowJson futureShow(long id) {
        ShowJson show = new ShowJson();
        show.id = id;
        show.price = 200;
        show.date = "2026-11-02";
        show.startTime = "09:30:00";
        return show;
    }

    private void attemptReservation(BookingService service, long userId, long showId,
                                    CountDownLatch ready, CountDownLatch start,
                                    AtomicInteger created, AtomicInteger rejected) {
        ready.countDown();
        try {
            start.await();
            service.create(userId, showId, List.of("B4"));
            created.incrementAndGet();
        } catch (ResponseStatusException expectedConflict) {
            rejected.incrementAndGet();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Reservation test interrupted", interrupted);
        }
    }
}
