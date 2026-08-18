package com.cinebook.collection.controller;

import com.cinebook.common.json.JsonLoaderService;
import com.cinebook.common.json.JsonLoaderService.CollectionJson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/collections")
public class CollectionController {

    @Autowired
    private JsonLoaderService jsonLoaderService;

    @GetMapping
    public ResponseEntity<List<CollectionJson>> getAllCollections() {
        List<CollectionJson> collections = jsonLoaderService.getData("data/cinebook_collections.json", CollectionJson.class);
        return ResponseEntity.ok(collections);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CollectionJson> getCollectionById(@PathVariable String id) {
        List<CollectionJson> collections = jsonLoaderService.getData("data/cinebook_collections.json", CollectionJson.class);
        return collections.stream()
                .filter(c -> c.id != null && c.id.equals(id))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}