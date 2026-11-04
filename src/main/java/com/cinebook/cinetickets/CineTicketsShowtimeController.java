package com.cinebook.cinetickets;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/cinetickets/movies/{imdbId}/showtimes")
public class CineTicketsShowtimeController {
    private final CineTicketsShowtimeService service;

    public CineTicketsShowtimeController(CineTicketsShowtimeService service) { this.service = service; }

    @GetMapping("/dates")
    public CineTicketsShowtimeService.BookingDates dates(@PathVariable String imdbId) {
        return service.dates(imdbId);
    }

    @GetMapping
    public List<CineTicketsShowtimeService.TheaterSchedule> shows(
            @PathVariable String imdbId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.shows(imdbId, date);
    }

    @GetMapping("/{showId}")
    public CineTicketsShowtimeService.ScreeningSelection validate(
            @PathVariable String imdbId,
            @PathVariable long showId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.validateSelection(imdbId, date, showId);
    }
}
