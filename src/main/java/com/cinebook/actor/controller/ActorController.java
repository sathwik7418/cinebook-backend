package com.cinebook.actor.controller;

import com.cinebook.common.json.JsonLoaderService;
import com.cinebook.common.json.JsonLoaderService.ActorJson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/actors")
public class ActorController {

    @Autowired
    private JsonLoaderService jsonLoaderService;

    @GetMapping
    public ResponseEntity<List<ActorJson>> getAllActors() {
        List<ActorJson> actors = jsonLoaderService.getData("data/actors.json", ActorJson.class);
        return ResponseEntity.ok(actors);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ActorJson> getActorById(@PathVariable int id) {
        List<ActorJson> actors = jsonLoaderService.getData("data/actors.json", ActorJson.class);
        return actors.stream()
                .filter(a -> a.id == id)
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}