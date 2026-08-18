package com.cinebook.movie.controller;

import com.cinebook.common.json.JsonLoaderService;
import com.cinebook.common.json.JsonLoaderService.MovieJson;
import com.cinebook.common.json.JsonLoaderService.ShowJson;
import com.cinebook.common.json.JsonLoaderService.CinemaJson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/movies")
public class MovieController {

    @Autowired
    private JsonLoaderService jsonLoaderService;

    @GetMapping
    public ResponseEntity<List<MovieJson>> getAllMovies() {
        List<MovieJson> movies = jsonLoaderService.getData("data/cinebook_movies.json", MovieJson.class);
        return ResponseEntity.ok(movies);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MovieJson> getMovieById(@PathVariable int id) {
        List<MovieJson> movies = jsonLoaderService.getData("data/cinebook_movies.json", MovieJson.class);
        return movies.stream()
                .filter(movie -> movie.id == id)
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
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
        public int id;
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

    @GetMapping("/{movieId}/cinemas")
    public ResponseEntity<List<CinemaWithShows>> getCinemasForMovie(
            @PathVariable int movieId,
            @RequestParam String date) {

        List<ShowJson> shows = jsonLoaderService.getData("data/shows.json", ShowJson.class);
        List<CinemaJson> cinemas = jsonLoaderService.getData("data/cinemas.json", CinemaJson.class);

        List<ShowJson> filteredShows = shows.stream()
                .filter(s -> s.movieId == movieId && date.equals(s.date))
                .collect(Collectors.toList());

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
                summary.availableSeats = s.availableSeats;
                return summary;
            }).collect(Collectors.toList());

            cws.shows.sort(Comparator.comparing(s -> s.startTime));
            result.add(cws);
        }

        return ResponseEntity.ok(result);
    }
}