package com.cinebook.cinema.controller;

import com.cinebook.common.json.JsonLoaderService;
import com.cinebook.common.json.JsonLoaderService.CinemaJson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cinemas")
public class CinemaController {

    @Autowired
    private JsonLoaderService jsonLoaderService;

    @GetMapping
    public ResponseEntity<List<CinemaJson>> getAllCinemas() {
        List<CinemaJson> cinemas = jsonLoaderService.getData("data/cinemas.json", CinemaJson.class);
        return ResponseEntity.ok(cinemas);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CinemaJson> getCinemaById(@PathVariable int id) {
        List<CinemaJson> cinemas = jsonLoaderService.getData("data/cinemas.json", CinemaJson.class);
        return cinemas.stream()
                .filter(c -> c.id == id)
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}