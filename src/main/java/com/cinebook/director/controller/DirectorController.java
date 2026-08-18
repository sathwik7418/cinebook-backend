package com.cinebook.director.controller;

import com.cinebook.common.json.JsonLoaderService;
import com.cinebook.common.json.JsonLoaderService.DirectorJson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/directors")
public class DirectorController {

    @Autowired
    private JsonLoaderService jsonLoaderService;

    @GetMapping
    public ResponseEntity<List<DirectorJson>> getAllDirectors() {
        List<DirectorJson> directors = jsonLoaderService.getData("data/directors.json", DirectorJson.class);
        return ResponseEntity.ok(directors);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DirectorJson> getDirectorById(@PathVariable int id) {
        List<DirectorJson> directors = jsonLoaderService.getData("data/directors.json", DirectorJson.class);
        return directors.stream()
                .filter(d -> d.id == id)
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}