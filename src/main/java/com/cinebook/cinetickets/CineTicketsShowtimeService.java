package com.cinebook.cinetickets;

import com.cinebook.booking.service.BookingService;
import com.cinebook.common.json.JsonLoaderService;
import com.cinebook.common.json.JsonLoaderService.CinemaJson;
import com.cinebook.common.json.JsonLoaderService.ShowJson;
import com.cinebook.show.service.DynamicShowtimeService;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Joins OMDb identities to the existing, deterministic CineBook demo schedule. */
@Service
public class CineTicketsShowtimeService {
    private final JsonLoaderService data;
    private final DynamicShowtimeService schedules;
    private final BookingService bookings;
    private final CineTicketsBookingRegistry registry;

    public CineTicketsShowtimeService(JsonLoaderService data, DynamicShowtimeService schedules,
                                      BookingService bookings, CineTicketsBookingRegistry registry) {
        this.data = data;
        this.schedules = schedules;
        this.bookings = bookings;
        this.registry = registry;
    }

    public BookingDates dates(String imdbId) {
        BookingMapping mapping = registry.byImdbId(imdbId);
        if (mapping == null) return new BookingDates(false, true, null, List.of());
        List<LocalDate> dates = schedules.getAvailableDates().stream()
                .filter(date -> !schedules.getCineTicketsShows(mapping.bookingMovieId(), null, date).isEmpty())
                .sorted().toList();
        return new BookingDates(true, true, mapping.bookingMovieId(), dates.stream().map(LocalDate::toString).toList());
    }

    public List<TheaterSchedule> shows(String imdbId, LocalDate date) {
        BookingMapping mapping = requireMapping(imdbId);
        if (!schedules.getAvailableDates().contains(date)) return List.of();
        Map<Integer, CinemaJson> cinemas = data.getData("data/cinemas.json", CinemaJson.class).stream()
                .collect(Collectors.toMap(cinema -> cinema.id, Function.identity()));
        Map<Integer, List<ShowJson>> grouped = schedules.getCineTicketsShows(mapping.bookingMovieId(), null, date).stream()
                .collect(Collectors.groupingBy(show -> show.cinemaId));
        return grouped.entrySet().stream().map(entry -> {
                    CinemaJson cinema = cinemas.get(entry.getKey());
                    if (cinema == null) return null;
                    List<Screening> screenings = entry.getValue().stream()
                            .sorted(Comparator.comparing(show -> show.startTime))
                            .map(this::screening).toList();
                    return new TheaterSchedule(cinema.id, cinema.name, cinema.city, cinema.address,
                            screenings.stream().map(Screening::format).distinct().sorted().toList(), screenings);
                }).filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(TheaterSchedule::city).thenComparing(TheaterSchedule::name)).toList();
    }

    public ScreeningSelection validateSelection(String imdbId, LocalDate date, long showId) {
        BookingMapping mapping = requireMapping(imdbId);
        if (!schedules.getAvailableDates().contains(date)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Screening date is unavailable.");
        ShowJson show = schedules.getCineTicketsShows(mapping.bookingMovieId(), null, date).stream()
                .filter(candidate -> candidate.id == showId).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Screening is no longer available."));
        int available = bookings.availableSeats(show.id);
        if (available <= 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "This screening is sold out.");
        CinemaJson cinema = data.getData("data/cinemas.json", CinemaJson.class).stream()
                .filter(candidate -> candidate.id == show.cinemaId).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Theater is unavailable."));
        return new ScreeningSelection(mapping.bookingMovieId(), cinema.id, cinema.name, cinema.city,
                cinema.address, screening(show), date, available);
    }

    private Screening screening(ShowJson show) {
        return new Screening(show.id, show.movieId, show.cinemaId, show.screen, show.format,
                show.date, show.startTime, show.endTime, show.price, bookings.availableSeats(show.id));
    }

    private BookingMapping requireMapping(String imdbId) {
        BookingMapping mapping = registry.byImdbId(imdbId);
        if (mapping == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No CineBook demo screening is mapped to this movie.");
        return mapping;
    }

    public record BookingMapping(String imdbId, int bookingMovieId, String title, int year, int runtimeMinutes) {}
    public record MappingFile(List<BookingMapping> mappings) {}
    public record BookingDates(boolean bookable, boolean demoSchedule, Integer bookingMovieId, List<String> dates) {}
    public record Screening(long id, int movieId, int cinemaId, String screen, String format, String date,
                            String startTime, String endTime, int price, int availableSeats) {}
    public record TheaterSchedule(int id, String name, String city, String address, List<String> formats,
                                  List<Screening> shows) {}
    public record ScreeningSelection(int bookingMovieId, int theaterId, String theaterName, String city,
                                     String address, Screening show, LocalDate date, int availableSeats) {}
}
