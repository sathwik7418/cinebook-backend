package com.cinebook.cinetickets;

import com.cinebook.booking.service.BookingService;
import com.cinebook.common.json.JsonLoaderService;
import com.cinebook.show.service.DynamicShowtimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class CineTicketsShowtimeServiceTest {
    @TempDir Path temp;
    private CineTicketsShowtimeService service;
    private DynamicShowtimeService schedules;
    private BookingService bookings;
    private CineTicketsBookingRegistry registry;
    private CuratedCatalogRepository curated;

    @BeforeEach
    void setUp() {
        JsonLoaderService catalog = new JsonLoaderService();
        catalog.loadAll();
        Clock clock = Clock.fixed(Instant.parse("2026-11-04T03:00:00Z"), ZoneId.of("UTC"));
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        curated = new CuratedCatalogRepository(mapper);
        registry = new CineTicketsBookingRegistry(mapper, curated, catalog);
        schedules = new DynamicShowtimeService(catalog, clock, registry);
        bookings = new BookingService(temp.toString(), schedules, clock);
        service = new CineTicketsShowtimeService(catalog, schedules, bookings, registry);
    }

    @Test
    void mappedOmdbMovieUsesItsDedicatedBookingIdAndRollingSchedule() {
        var dates = service.dates("tt15239678");
        assertTrue(dates.bookable());
        assertTrue(dates.demoSchedule());
        assertEquals(1000, dates.bookingMovieId());
        assertEquals(7, dates.dates().size());
        assertEquals(LocalDate.now(Clock.fixed(Instant.parse("2026-11-04T03:00:00Z"), ZoneId.of("UTC")).withZone(ZoneId.of("Asia/Kolkata"))).toString(), dates.dates().get(0));

        var theaters = service.shows("tt15239678", LocalDate.parse(dates.dates().get(1)));
        assertFalse(theaters.isEmpty());
        var shows = theaters.stream().flatMap(theater -> theater.shows().stream()).toList();
        assertTrue(shows.stream().allMatch(show -> show.movieId() == 1000));
        assertTrue(shows.stream().allMatch(show -> show.format().equals("2D") || show.format().equals("3D")));
        assertEquals(shows.size(), shows.stream().map(CineTicketsShowtimeService.Screening::id).distinct().count());
        assertTrue(shows.stream().allMatch(show -> show.availableSeats() >= 0));
    }

    @Test
    void allCuratedFilmsHaveStableDistinctBookingIdsAndTheirShowIdsCannotCollide() {
        var mappingIds = registry.all().stream().sorted(java.util.Comparator.comparingInt(CineTicketsShowtimeService.BookingMapping::bookingMovieId))
                .map(CineTicketsShowtimeService.BookingMapping::imdbId).toList();
        var date = schedules.getAvailableDates().get(1);
        var ids = mappingIds.stream().map(id -> service.dates(id)).toList();
        assertTrue(ids.stream().allMatch(CineTicketsShowtimeService.BookingDates::bookable));
        assertEquals(38, mappingIds.size(), "each verified editorial film is mapped once");
        assertEquals(38, ids.stream().map(CineTicketsShowtimeService.BookingDates::bookingMovieId).distinct().count());
        assertEquals(java.util.stream.IntStream.range(1000, 1038).boxed().toList(),
                ids.stream().map(CineTicketsShowtimeService.BookingDates::bookingMovieId).sorted().toList());
        assertTrue(ids.stream().allMatch(value -> value.bookingMovieId() > 241));
        assertEquals(38, curated.get().films().stream().map(CuratedCatalog.CuratedFilm::imdbId).distinct().count());
        assertEquals(38, curated.get().collections().stream().flatMap(collection -> collection.movieIds().stream()).distinct().count(),
                "repeated collection appearances resolve to one curated identity");
        assertEquals(1000, registry.byImdbId("tt15239678").bookingMovieId());
        assertEquals(1001, registry.byImdbId("tt0816692").bookingMovieId());
        assertEquals(1002, registry.byImdbId("tt15398776").bookingMovieId());
        assertEquals(1003, registry.byImdbId("tt1375666").bookingMovieId());
        assertEquals(1004, registry.byImdbId("tt0468569").bookingMovieId());
        assertEquals(1005, registry.byImdbId("tt9362722").bookingMovieId());
        assertEquals(1006, registry.byImdbId("tt8178634").bookingMovieId());
        assertEquals(1007, registry.byImdbId("tt12735488").bookingMovieId());
        var combined = mappingIds.stream().flatMap(id -> service.shows(id, date).stream())
                .flatMap(theater -> theater.shows().stream()).toList();
        var legacy = schedules.getShows(null, null, date);
        var allIds = java.util.stream.Stream.concat(
                combined.stream().map(CineTicketsShowtimeService.Screening::id),
                legacy.stream().map(show -> show.id)).toList();
        assertEquals(allIds.size(), allIds.stream().distinct().count(), "CineTickets show IDs must not collide with legacy show IDs");
        assertEquals(combined.size(), combined.stream().map(CineTicketsShowtimeService.Screening::id).distinct().count());
        assertTrue(combined.stream().allMatch(show -> schedules.getShow(show.id()) != null));
    }

    @Test
    void expandedRegistryProducesTheSameShowIdsAfterScheduleServiceRecreation() {
        LocalDate date = schedules.getAvailableDates().get(1);
        JsonLoaderService restartedCatalog = new JsonLoaderService();
        restartedCatalog.loadAll();
        DynamicShowtimeService restarted = new DynamicShowtimeService(restartedCatalog,
                Clock.fixed(Instant.parse("2026-11-04T03:00:00Z"), ZoneId.of("UTC")), registry);
        for (int bookingMovieId = 1000; bookingMovieId < 1038; bookingMovieId++) {
            var originalIds = schedules.getCineTicketsShows(bookingMovieId, null, date).stream().map(show -> show.id).toList();
            var restartedIds = restarted.getCineTicketsShows(bookingMovieId, null, date).stream().map(show -> show.id).toList();
            assertEquals(originalIds, restartedIds, "demo show IDs must remain stable for booking movie " + bookingMovieId);
        }
    }

    @Test
    void seatInventoryIsIsolatedBetweenTwoCuratedMoviesAtTheSameTheaterAndDate() {
        LocalDate date = schedules.getAvailableDates().get(1);
        var dune = schedules.getCineTicketsShows(1000, null, date).get(0);
        var interstellar = schedules.getCineTicketsShows(1001, dune.cinemaId, date).stream()
                .filter(show -> show.screen.equals(dune.screen) && show.startTime.equals(dune.startTime)).findFirst().orElseThrow();
        assertNotEquals(dune.id, interstellar.id);

        bookings.create(7, dune.id, java.util.List.of("A1"));
        assertEquals(143, bookings.availableSeats(dune.id));
        assertEquals(144, bookings.availableSeats(interstellar.id));
        var otherMovieBooking = bookings.create(8, interstellar.id, java.util.List.of("A1"));
        assertEquals(1001, otherMovieBooking.movieId);
        assertEquals(143, bookings.availableSeats(interstellar.id));
    }

    @Test
    void validationReturnsExistingSeatSelectionIdentifiersAndCurrentAvailability() {
        LocalDate date = schedules.getAvailableDates().get(1);
        var show = schedules.getCineTicketsShows(1000, null, date).get(0);
        var selection = service.validateSelection("tt15239678", date, show.id);
        assertEquals(1000, selection.bookingMovieId());
        assertEquals(show.cinemaId, selection.theaterId());
        assertEquals(show.id, selection.show().id());
        assertEquals(date, selection.date());
        assertTrue(selection.availableSeats() > 0);
    }

    @Test
    void unknownOrClassicMoviesDoNotReceiveUnrelatedLegacyShowtimes() {
        assertFalse(service.dates("tt0111161").bookable(), "unconfigured movies do not receive invented schedules");
        assertFalse(service.dates("tt0000000").bookable());
        assertTrue(service.shows("tt15239678", LocalDate.parse("2026-11-12")).isEmpty(), "dates outside the rolling window are not bookable");
    }

    @Test
    void existingBookingEnginePersistsCuratedMovieIdentityAndRejectsConflictingSeats() {
        LocalDate date = schedules.getAvailableDates().get(1);
        var show = schedules.getCineTicketsShows(1000, null, date).get(0);
        var booking = bookings.create(7, show.id, java.util.List.of("A1"));
        assertEquals(show.id, booking.showId);
        assertEquals(1000, booking.movieId);
        assertEquals("Dune: Part Two", booking.movieTitle);
        assertEquals(143, bookings.availableSeats(show.id));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> bookings.create(8, show.id, java.util.List.of("A1")));
    }
}
