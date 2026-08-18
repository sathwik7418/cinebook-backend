package com.cinebook.genre.controller;

import com.cinebook.common.json.JsonLoaderService;
import com.cinebook.common.json.JsonLoaderService.GenreJson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/genres")
public class GenreController {

    @Autowired
    private JsonLoaderService jsonLoaderService;

    @GetMapping
    public ResponseEntity<List<GenreJson>> getAllGenres() {
        List<GenreJson> genres = jsonLoaderService.getData("data/genres.json", GenreJson.class);
        return ResponseEntity.ok(genres);
    }

    @GetMapping("/{id}")
    public ResponseEntity<GenreJson> getGenreById(@PathVariable int id) {
        List<GenreJson> genres = jsonLoaderService.getData("data/genres.json", GenreJson.class);
        return genres.stream()
                .filter(g -> g.id == id)
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}