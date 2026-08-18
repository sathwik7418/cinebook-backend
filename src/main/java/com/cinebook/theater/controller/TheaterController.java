package com.cinebook.theater.controller;

import com.cinebook.common.json.JsonLoaderService;
import com.cinebook.common.json.JsonLoaderService.CinemaJson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/theaters")
public class TheaterController {

    @Autowired
    private JsonLoaderService jsonLoaderService;

    // Inner class to match frontend Theater type
    public static class Theater {
        public int id;
        public String name;
        public int cinemaId;
        public String createdAt;
        public String updatedAt;
    }

    @GetMapping
    public ResponseEntity<List<Theater>> getAllTheaters() {
        List<CinemaJson> cinemas = jsonLoaderService.getData("data/cinemas.json", CinemaJson.class);
        List<Theater> theaters = new ArrayList<>();
        int id = 1;
        for (CinemaJson cinema : cinemas) {
            Theater theater = new Theater();
            theater.id = id++;
            theater.name = cinema.name;
            theater.cinemaId = cinema.id; // assuming cinema.id is the cinema's id
            theater.createdAt = ""; // empty string to match frontend optional string
            theater.updatedAt = ""; // empty string to match frontend optional string
            theaters.add(theater);
        }
        return ResponseEntity.ok(theaters);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Theater> getTheaterById(@PathVariable int id) {
        List<CinemaJson> cinemas = jsonLoaderService.getData("data/cinemas.json", CinemaJson.class);
        return cinemas.stream()
                .filter(c -> c.id == id)
                .map(c -> {
                    Theater theater = new Theater();
                    theater.id = id; // we don't have the actual id, but we can use the cinema id as theater id for simplicity
                    theater.name = c.name;
                    theater.cinemaId = c.id;
                    theater.createdAt = "";
                    theater.updatedAt = "";
                    return theater;
                })
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}