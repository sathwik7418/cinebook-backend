package com.cinebook.movie.controller;

import com.cinebook.common.json.JsonLoaderService;
import com.cinebook.common.json.JsonLoaderService.MovieJson;
import com.cinebook.common.json.JsonLoaderService.ShowJson;
import com.cinebook.common.json.JsonLoaderService.CinemaJson;
import com.cinebook.booking.service.BookingService;
import com.cinebook.cinetickets.CineTicketsBookingRegistry;
import com.cinebook.cinetickets.CineTicketsService;
import com.cinebook.cinetickets.CineTicketsShowtimeService.BookingMapping;
import com.cinebook.cinetickets.CineTicketsMovie;
import com.cinebook.show.service.DynamicShowtimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.text.Normalizer;
import java.util.Locale;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/movies")
public class MovieController {

    @Autowired
    private JsonLoaderService jsonLoaderService;

    @Autowired
    private DynamicShowtimeService dynamicShowtimeService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private CineTicketsBookingRegistry cineTicketsBookingRegistry;

    @Autowired
    private CineTicketsService cineTicketsService;

    @GetMapping
    public ResponseEntity<List<MovieJson>> getAllMovies() {
        List<MovieJson> movies = jsonLoaderService.getData("data/cinebook_movies.json", MovieJson.class);
        return ResponseEntity.ok(movies);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MovieJson> getMovieById(@PathVariable int id) {
        List<MovieJson> movies = jsonLoaderService.getData("data/cinebook_movies.json", MovieJson.class);
        var legacyMovie = movies.stream()
                .filter(movie -> movie.id == id)
                .findFirst();
        if (legacyMovie.isPresent()) return ResponseEntity.ok(legacyMovie.get());
        BookingMapping mapping = cineTicketsBookingRegistry.byBookingId(id);
        if (mapping == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(toBookingMovie(cineTicketsService.movie(mapping.imdbId()), mapping));
    }

    @GetMapping("/title/{title}")
    public ResponseEntity<List<MovieJson>> getMoviesByTitleContaining(@PathVariable String title) {
        List<MovieJson> movies = jsonLoaderService.getData("data/cinebook_movies.json", MovieJson.class);
        List<MovieJson> filtered = movies.stream()
                .filter(m -> m.title.toLowerCase().contains(title.toLowerCase()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(filtered);
    }

    @GetMapping("/genre/{genre}")
    public ResponseEntity<List<MovieJson>> getMoviesByGenre(@PathVariable String genre) {
        List<MovieJson> movies = jsonLoaderService.getData("data/cinebook_movies.json", MovieJson.class);
        List<MovieJson> filtered = movies.stream()
                .filter(m -> m.genres != null && m.genres.stream().anyMatch(g -> g.toLowerCase().contains(genre.toLowerCase())))
                .collect(Collectors.toList());
        return ResponseEntity.ok(filtered);
    }

    // ---- Choose Your Show: theaters + showtimes for a movie on a given date ----

    public static class ShowSummary {
        public long id;
        public String screen;
        public String format;
        public String startTime;
        public String endTime;
        public int price;
        public int availableSeats;
    }

    public static class CinemaWithShows {
        public int id;
        public String name;
        public String city;
        public String address;
        public List<ShowSummary> shows;
    }

    private MovieJson toBookingMovie(CineTicketsMovie source, BookingMapping mapping) {
        if (source.imdbId() == null || !source.imdbId().equalsIgnoreCase(mapping.imdbId())
                || source.year() == null || source.year() != mapping.year()
                || !normalizeTitle(source.title()).equals(normalizeTitle(mapping.title()))) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "The mapped movie metadata could not be verified.");
        }
        MovieJson movie = new MovieJson();
        movie.id = mapping.bookingMovieId();
        movie.imdbId = source.imdbId();
        movie.title = source.title();
        movie.description = source.plot();
        movie.poster = source.posterUrl();
        movie.language = source.language() == null ? null : String.join(", ", source.language());
        movie.genres = source.genres();
        movie.duration = source.runtimeMinutes() == null ? mapping.runtimeMinutes() : source.runtimeMinutes();
        movie.rating = source.imdbRating() == null ? null : source.imdbRating().doubleValue();
        movie.releaseDate = source.releasedDate() == null ? null : source.releasedDate().toString();
        movie.director = source.director();
        movie.cast = source.cast();
        movie.status = "DEMO_SCREENING";
        return movie;
    }

    private String normalizeTitle(String title) {
        return title == null ? "" : Normalizer.normalize(title, Normalizer.Form.NFKD)
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    @GetMapping("/{movieId}/cinemas")
    public ResponseEntity<List<CinemaWithShows>> getCinemasForMovie(
            @PathVariable int movieId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate date) {

        List<CinemaJson> cinemas = jsonLoaderService.getData("data/cinemas.json", CinemaJson.class);
        List<ShowJson> filteredShows = dynamicShowtimeService.supportsCineTicketsMovie(movieId)
                ? dynamicShowtimeService.getCineTicketsShows(movieId, null, date)
                : dynamicShowtimeService.getShows(movieId, null, date);

        Map<Integer, List<ShowJson>> groupedByCinema = filteredShows.stream()
                .collect(Collectors.groupingBy(s -> s.cinemaId));

        List<CinemaWithShows> result = new ArrayList<>();

        for (Map.Entry<Integer, List<ShowJson>> entry : groupedByCinema.entrySet()) {
            CinemaJson cinema = cinemas.stream()
                    .filter(c -> c.id == entry.getKey())
                    .findFirst()
                    .orElse(null);

            if (cinema == null) {
                // Show references a cinemaId with no matching cinema record; skip rather than guess.
                continue;
            }

            CinemaWithShows cws = new CinemaWithShows();
            cws.id = cinema.id;
            cws.name = cinema.name;
            cws.city = cinema.city;
            cws.address = cinema.address;
            cws.shows = entry.getValue().stream().map(s -> {
                ShowSummary summary = new ShowSummary();
                summary.id = s.id;
                summary.screen = s.screen;
                summary.format = s.format;
                summary.startTime = s.startTime;
                summary.endTime = s.endTime;
                summary.price = s.price;
                summary.availableSeats = bookingService.availableSeats(s.id);
                return summary;
            }).collect(Collectors.toList());

            cws.shows.sort(Comparator.comparing(s -> s.startTime));
            result.add(cws);
        }

        return ResponseEntity.ok(result);
    }
}
