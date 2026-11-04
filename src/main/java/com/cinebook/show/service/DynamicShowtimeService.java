package com.cinebook.show.service;

import com.cinebook.common.json.JsonLoaderService;
import com.cinebook.common.json.JsonLoaderService.CinemaJson;
import com.cinebook.common.json.JsonLoaderService.MovieJson;
import com.cinebook.common.json.JsonLoaderService.ShowJson;
import com.cinebook.cinetickets.CineTicketsBookingRegistry;
import com.cinebook.cinetickets.CineTicketsShowtimeService.BookingMapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Creates a stable, fictional schedule from the catalog without storing per-day data. */
@Service
public class DynamicShowtimeService {
    public static final ZoneId SCHEDULE_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final List<LocalTime> STARTS = List.of(
            LocalTime.of(9, 30), LocalTime.of(13, 30), LocalTime.of(17, 30));
    private static final LocalTime CLOSE = LocalTime.of(23, 0);
    private static final int TURNAROUND_MINUTES = 20;
    private static final int SEAT_CAPACITY = 8 * 18;

    private final JsonLoaderService data;
    private final Clock clock;
    private final CineTicketsBookingRegistry cineTicketsRegistry;

    @Autowired
    public DynamicShowtimeService(JsonLoaderService data, Clock clock, CineTicketsBookingRegistry cineTicketsRegistry) {
        this.data = data;
        this.clock = clock;
        this.cineTicketsRegistry = cineTicketsRegistry;
    }

    /** Kept for focused legacy schedule tests and simple standalone use. */
    public DynamicShowtimeService(JsonLoaderService data, Clock clock) {
        this(data, clock, null);
    }

    public List<LocalDate> getAvailableDates() {
        LocalDate today = LocalDate.now(clock.withZone(SCHEDULE_ZONE));
        return java.util.stream.IntStream.range(0, 7).mapToObj(today::plusDays).toList();
    }

    public boolean supportsCineTicketsMovie(int bookingMovieId) {
        return cineTicketsRegistry != null && cineTicketsRegistry.byBookingId(bookingMovieId) != null;
    }

    public List<ShowJson> getShows(Integer movieId, Integer cinemaId, LocalDate date) {
        LocalDate today = LocalDate.now(clock.withZone(SCHEDULE_ZONE));
        if (date.isBefore(today) || date.isAfter(today.plusDays(6))) return List.of();

        List<MovieJson> movies = data.getData("data/cinebook_movies.json", MovieJson.class).stream()
                .filter(movie -> "NOW_SHOWING".equalsIgnoreCase(movie.status) && movie.duration > 0)
                .sorted(Comparator.comparingInt(movie -> movie.id)).toList();
        List<CinemaJson> cinemas = data.getData("data/cinemas.json", CinemaJson.class);
        List<ShowJson> result = new ArrayList<>();
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(SCHEDULE_ZONE));

        for (CinemaJson cinema : cinemas) {
            if (cinemaId != null && cinema.id != cinemaId) continue;
            if (cinema.screens < 1 || movies.isEmpty()) continue;
            for (int screen = 1; screen <= cinema.screens; screen++) {
                for (int slot = 0; slot < STARTS.size(); slot++) {
                    int ordinal = (screen - 1) * STARTS.size() + slot;
                    int offset = Math.floorMod((int) (date.toEpochDay() + cinema.id * 17L), movies.size());
                    MovieJson movie = movies.get((offset + ordinal) % movies.size());
                    LocalTime start = STARTS.get(slot);
                    LocalTime end = start.plusMinutes(movie.duration);
                    if (end.plusMinutes(TURNAROUND_MINUTES).isAfter(CLOSE)) continue;
                    ZonedDateTime startsAt = date.atTime(start).atZone(SCHEDULE_ZONE);
                    if (date.equals(today) && !startsAt.isAfter(now)) continue;

                    ShowJson show = new ShowJson();
                    show.id = stableId(date, movie.id, cinema.id, screen, slot);
                    show.movieId = movie.id;
                    show.movieTitle = movie.title;
                    show.cinemaId = cinema.id;
                    show.screen = "Screen " + screen;
                    show.format = chooseFormat(cinema, movie, screen, slot);
                    show.date = date.toString();
                    show.startTime = start.format(TIME_FORMAT);
                    show.endTime = end.format(TIME_FORMAT);
                    show.price = priceFor(show.format, screen);
                    // The booking service validates this same A-H x 1-18 seat layout.
                    show.availableSeats = SEAT_CAPACITY;
                    if (movieId == null || show.movieId == movieId) result.add(show);
                }
            }
        }
        result.sort(Comparator.comparing((ShowJson show) -> show.startTime)
                .thenComparingInt(show -> show.cinemaId).thenComparing(show -> show.screen));
        return result;
    }

    public ShowJson getShow(long id) {
        if (cineTicketsRegistry != null && id > 0) {
            long epochDay = id / 1_000_000_000L;
            long suffix = id % 1_000_000_000L;
            if (suffix >= 200_000_000L) {
                LocalDate date;
                try { date = LocalDate.ofEpochDay(epochDay); }
                catch (RuntimeException invalidId) { return null; }
                if (!getAvailableDates().contains(date)) return null;
                for (BookingMapping mapping : cineTicketsRegistry.all()) {
                    long remainder = suffix - 200_000_000L - mapping.bookingMovieId() * 1_000L;
                    if (remainder < 0) continue;
                    int cinemaId = (int) (remainder / 1_000_000L);
                    int screenSlot = (int) (remainder % 1_000_000L);
                    int screen = screenSlot / 10;
                    int slot = screenSlot % 10;
                    if (slot >= STARTS.size() || screen < 1) continue;
                    CinemaJson cinema = data.getData("data/cinemas.json", CinemaJson.class).stream()
                            .filter(item -> item.id == cinemaId && item.screens >= screen).findFirst().orElse(null);
                    if (cinema == null) continue;
                    ShowJson candidate = cineTicketsShow(mapping, cinema, date, screen, slot);
                    if (candidate != null && candidate.id == id) return candidate;
                }
                return null;
            }
        }
        for (LocalDate date : getAvailableDates()) {
            for (ShowJson show : getShows(null, null, date)) if (show.id == id) return show;
        }
        return null;
    }

    /** Generates a deterministic CineTickets schedule in a separate show-ID namespace. */
    public List<ShowJson> getCineTicketsShows(int bookingMovieId, Integer cinemaId, LocalDate date) {
        if (cineTicketsRegistry == null || cineTicketsRegistry.byBookingId(bookingMovieId) == null) return List.of();
        LocalDate today = LocalDate.now(clock.withZone(SCHEDULE_ZONE));
        if (date.isBefore(today) || date.isAfter(today.plusDays(6))) return List.of();
        List<CinemaJson> cinemas = data.getData("data/cinemas.json", CinemaJson.class);
        List<ShowJson> result = new ArrayList<>();
        BookingMapping movie = cineTicketsRegistry.byBookingId(bookingMovieId);
        for (CinemaJson cinema : cinemas) {
            if (cinemaId != null && cinema.id != cinemaId || cinema.screens < 1) continue;
            for (int screen = 1; screen <= cinema.screens; screen++) {
                for (int slot = 0; slot < STARTS.size(); slot++) {
                    ShowJson show = cineTicketsShow(movie, cinema, date, screen, slot);
                    if (show != null) result.add(show);
                }
            }
        }
        result.sort(Comparator.comparing((ShowJson show) -> show.startTime)
                .thenComparingInt(show -> show.cinemaId).thenComparing(show -> show.screen));
        return result;
    }

    private ShowJson cineTicketsShow(BookingMapping movie, CinemaJson cinema, LocalDate date, int screen, int slot) {
        LocalDate today = LocalDate.now(clock.withZone(SCHEDULE_ZONE));
        LocalTime start = STARTS.get(slot);
        LocalTime end = start.plusMinutes(movie.runtimeMinutes());
        if (end.plusMinutes(TURNAROUND_MINUTES).isAfter(CLOSE)) return null;
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(SCHEDULE_ZONE));
        if (date.equals(today) && !date.atTime(start).atZone(SCHEDULE_ZONE).isAfter(now)) return null;
        ShowJson show = new ShowJson();
        show.id = cineTicketsStableId(date, movie.bookingMovieId(), cinema.id, screen, slot);
        show.movieId = movie.bookingMovieId();
        show.movieTitle = movie.title();
        show.cinemaId = cinema.id;
        show.screen = "Screen " + screen;
        show.format = cineTicketsFormat(cinema, movie.bookingMovieId(), screen, slot);
        show.date = date.toString();
        show.startTime = start.format(TIME_FORMAT);
        show.endTime = end.format(TIME_FORMAT);
        show.price = priceFor(show.format, screen);
        show.availableSeats = SEAT_CAPACITY;
        return show;
    }

    private long cineTicketsStableId(LocalDate date, int movieId, int cinemaId, int screen, int slot) {
        // Reserve the 200M suffix namespace; legacy schedules remain below 101M.
        return date.toEpochDay() * 1_000_000_000L + 200_000_000L + cinemaId * 1_000_000L
                + movieId * 1_000L + screen * 10L + slot;
    }

    private String cineTicketsFormat(CinemaJson cinema, int movieId, int screen, int slot) {
        List<String> formats = cinema.formats == null ? List.of() : cinema.formats;
        boolean supports2d = formats.contains("2D");
        boolean supports3d = formats.contains("3D");
        if (supports3d && Math.floorMod(movieId + screen + slot, 3) == 0) return "3D";
        if (supports2d) return "2D";
        return supports3d ? "3D" : "2D";
    }

    private long stableId(LocalDate date, int movieId, int cinemaId, int screen, int slot) {
        // Catalog identifiers fit these reserved decimal fields; epoch day scopes the ID by date.
        return date.toEpochDay() * 1_000_000_000L + cinemaId * 1_000_000L
                + movieId * 100L + screen * 10L + slot;
    }

    private String chooseFormat(CinemaJson cinema, MovieJson movie, int screen, int slot) {
        List<String> formats = cinema.formats == null ? List.of("2D") : cinema.formats;
        if (formats.contains("IMAX") && Math.floorMod(movie.id + screen + slot, 9) == 0) return "IMAX";
        if (formats.contains("4DX") && Math.floorMod(movie.id + screen + slot, 7) == 0) return "4DX";
        if (formats.contains("3D") && Math.floorMod(movie.id + screen + slot, 3) == 0) return "3D";
        return "2D";
    }

    private int priceFor(String format, int screen) {
        return switch (format) {
            case "IMAX" -> 380;
            case "4DX" -> 420;
            case "3D" -> 280;
            default -> 180 + (screen % 3) * 20;
        };
    }
}
