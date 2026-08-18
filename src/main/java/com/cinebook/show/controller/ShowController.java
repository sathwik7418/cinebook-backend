package com.cinebook.show.controller;

import com.cinebook.common.json.JsonLoaderService;
import com.cinebook.common.json.JsonLoaderService.ShowJson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/shows")
public class ShowController {

    @Autowired
    private JsonLoaderService jsonLoaderService;

    @GetMapping
    public ResponseEntity<List<ShowJson>> getAllShows() {
        List<ShowJson> shows = jsonLoaderService.getData("data/shows.json", ShowJson.class);
        return ResponseEntity.ok(shows);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShowJson> getShowById(@PathVariable int id) {
        List<ShowJson> shows = jsonLoaderService.getData("data/shows.json", ShowJson.class);
        return shows.stream()
                .filter(s -> s.id == id)
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Optional: filter by movieId
    @GetMapping("/movie/{movieId}")
    public ResponseEntity<List<ShowJson>> getShowsByMovieId(@PathVariable int movieId) {
        List<ShowJson> shows = jsonLoaderService.getData("data/shows.json", ShowJson.class);
        List<ShowJson> filtered = shows.stream()
                .filter(s -> s.movieId == movieId)
                .collect(Collectors.toList());
        return ResponseEntity.ok(filtered);
    }

    // Optional: filter by cinemaId
    @GetMapping("/cinema/{cinemaId}")
    public ResponseEntity<List<ShowJson>> getShowsByCinemaId(@PathVariable int cinemaId) {
        List<ShowJson> shows = jsonLoaderService.getData("data/shows.json", ShowJson.class);
        List<ShowJson> filtered = shows.stream()
                .filter(s -> s.cinemaId == cinemaId)
                .collect(Collectors.toList());
        return ResponseEntity.ok(filtered);
    }
}