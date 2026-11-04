package com.cinebook.show.controller;

import com.cinebook.common.json.JsonLoaderService.ShowJson;
import com.cinebook.show.service.DynamicShowtimeService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/shows")
public class ShowController {
    private final DynamicShowtimeService showtimeService;

    public ShowController(DynamicShowtimeService showtimeService) {
        this.showtimeService = showtimeService;
    }

    /** Date/time fields use ISO local values in Asia/Kolkata (dates: yyyy-MM-dd; times: HH:mm:ss). */
    @GetMapping
    public ResponseEntity<List<ShowJson>> getShows(
            @RequestParam(required = false) Integer movieId,
            @RequestParam(required = false) Integer cinemaId,
            @RequestParam(required = false) Integer theaterId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (movieId != null && movieId <= 0) return ResponseEntity.badRequest().build();
        if (cinemaId != null && cinemaId <= 0 || theaterId != null && theaterId <= 0)
            return ResponseEntity.badRequest().build();
        if (cinemaId != null && theaterId != null && !cinemaId.equals(theaterId))
            return ResponseEntity.badRequest().build();
        Integer selectedCinema = cinemaId != null ? cinemaId : theaterId;
        if (movieId != null && showtimeService.supportsCineTicketsMovie(movieId)) {
            if (date != null) return ResponseEntity.ok(showtimeService.getCineTicketsShows(movieId, selectedCinema, date));
            List<ShowJson> mappedShows = new ArrayList<>();
            for (LocalDate availableDate : showtimeService.getAvailableDates())
                mappedShows.addAll(showtimeService.getCineTicketsShows(movieId, selectedCinema, availableDate));
            return ResponseEntity.ok(mappedShows);
        }
        if (date != null) return ResponseEntity.ok(showtimeService.getShows(movieId, selectedCinema, date));
        List<ShowJson> shows = new ArrayList<>();
        for (LocalDate availableDate : showtimeService.getAvailableDates())
            shows.addAll(showtimeService.getShows(movieId, selectedCinema, availableDate));
        return ResponseEntity.ok(shows);
    }

    @GetMapping("/dates")
    public List<LocalDate> getDates() {
        return showtimeService.getAvailableDates();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShowJson> getShowById(@PathVariable long id) {
        ShowJson show = showtimeService.getShow(id);
        return show == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(show);
    }

    @GetMapping("/movie/{movieId}")
    public List<ShowJson> getShowsByMovieId(@PathVariable int movieId) {
        return getShows(movieId, null, null, null).getBody();
    }

    @GetMapping("/cinema/{cinemaId}")
    public List<ShowJson> getShowsByCinemaId(@PathVariable int cinemaId) {
        return getShows(null, cinemaId, null, null).getBody();
    }
}
