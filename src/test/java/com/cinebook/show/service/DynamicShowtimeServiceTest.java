package com.cinebook.show.service;

import com.cinebook.common.json.JsonLoaderService;
import com.cinebook.common.json.JsonLoaderService.CinemaJson;
import com.cinebook.common.json.JsonLoaderService.MovieJson;
import com.cinebook.common.json.JsonLoaderService.ShowJson;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DynamicShowtimeServiceTest {
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private DynamicShowtimeService serviceAt(String instant) {
        JsonLoaderService data = mock(JsonLoaderService.class);
        MovieJson movie = new MovieJson();
        movie.id = 7;
        movie.title = "Demo Feature";
        movie.status = "NOW_SHOWING";
        movie.duration = 180;
        CinemaJson cinema = new CinemaJson();
        cinema.id = 3;
        cinema.screens = 2;
        cinema.formats = List.of("2D", "3D", "IMAX");
        when(data.getData("data/cinebook_movies.json", MovieJson.class)).thenReturn(List.of(movie));
        when(data.getData("data/cinemas.json", CinemaJson.class)).thenReturn(List.of(cinema));
        return new DynamicShowtimeService(data, Clock.fixed(Instant.parse(instant), INDIA));
    }

    @Test
    void rollingWindowCrossesMonthAndYearAndRejectsEighthDay() {
        DynamicShowtimeService service = serviceAt("2026-12-30T03:00:00Z");
        assertEquals(List.of(LocalDate.parse("2026-12-30"), LocalDate.parse("2026-12-31"),
                LocalDate.parse("2027-01-01"), LocalDate.parse("2027-01-02"),
                LocalDate.parse("2027-01-03"), LocalDate.parse("2027-01-04"),
                LocalDate.parse("2027-01-05")), service.getAvailableDates());
        assertTrue(service.getShows(null, 3, LocalDate.parse("2027-01-05")).size() > 0);
        assertTrue(service.getShows(null, 3, LocalDate.parse("2027-01-06")).isEmpty());
    }

    @Test
    void todayExcludesStartedShowsAndFutureDateHasFullSchedule() {
        DynamicShowtimeService service = serviceAt("2026-11-01T09:00:00Z"); // 14:30 in India
        List<ShowJson> today = service.getShows(7, 3, LocalDate.parse("2026-11-01"));
        assertEquals(2, today.size());
        assertTrue(today.stream().allMatch(show -> show.availableSeats == 144));
        assertTrue(today.stream().allMatch(show -> show.startTime.compareTo("14:30:00") > 0));
        assertEquals(6, service.getShows(7, 3, LocalDate.parse("2026-11-02")).size());
    }

    @Test
    void sameScheduleAndRestartProduceSameIdsAndTimesAndFiltersStayScoped() {
        DynamicShowtimeService first = serviceAt("2026-11-01T03:00:00Z");
        DynamicShowtimeService restarted = serviceAt("2026-11-01T03:00:00Z");
        LocalDate day = LocalDate.parse("2026-11-02");
        List<ShowJson> one = first.getShows(7, 3, day);
        List<ShowJson> two = restarted.getShows(7, 3, day);
        assertEquals(one.stream().map(show -> show.id).toList(), two.stream().map(show -> show.id).toList());
        assertEquals(one.stream().map(show -> show.startTime).toList(), two.stream().map(show -> show.startTime).toList());
        assertTrue(first.getShows(99, 3, day).isEmpty());
        assertTrue(first.getShows(7, 99, day).isEmpty());
        assertNotNull(first.getShow(one.get(0).id));
    }

    @Test
    void leapDayIsHandledByCalendarArithmetic() {
        DynamicShowtimeService service = serviceAt("2024-02-28T03:00:00Z");
        assertEquals(LocalDate.parse("2024-02-29"), service.getAvailableDates().get(1));
    }

    @Test
    void midnightMovesTheRollingWindowForwardWithoutRestartState() {
        DynamicShowtimeService beforeMidnight = serviceAt("2026-11-01T18:29:59Z");
        DynamicShowtimeService afterMidnight = serviceAt("2026-11-01T18:30:01Z");
        assertEquals(LocalDate.parse("2026-11-01"), beforeMidnight.getAvailableDates().get(0));
        assertEquals(LocalDate.parse("2026-11-02"), afterMidnight.getAvailableDates().get(0));
        assertEquals(LocalDate.parse("2026-11-08"), afterMidnight.getAvailableDates().get(6));
    }

    @Test
    void catalogScheduleIdsAreUniqueAcrossDatesAndStableAfterServiceRecreation() {
        JsonLoaderService catalog = new JsonLoaderService();
        catalog.loadAll();
        Clock clock = Clock.fixed(Instant.parse("2026-11-01T03:00:00Z"), INDIA);
        DynamicShowtimeService first = new DynamicShowtimeService(catalog, clock);
        DynamicShowtimeService restarted = new DynamicShowtimeService(catalog, clock);
        List<ShowJson> generated = first.getAvailableDates().stream()
                .flatMap(date -> first.getShows(null, null, date).stream())
                .toList();
        assertFalse(generated.isEmpty());
        assertEquals(generated.size(), generated.stream().map(show -> show.id).distinct().count(),
                "schedule ID collision found across catalog schedules");
        for (ShowJson show : generated.subList(0, Math.min(10, generated.size()))) {
            assertTrue(Math.abs(show.id) < 9_007_199_254_740_992L, "show ID must remain exact in JavaScript numbers");
            ShowJson resolved = restarted.getShow(show.id);
            assertNotNull(resolved);
            assertEquals(show.id, resolved.id);
            assertEquals(show.startTime, resolved.startTime);
            assertEquals(show.date, resolved.date);
        }
    }
}
