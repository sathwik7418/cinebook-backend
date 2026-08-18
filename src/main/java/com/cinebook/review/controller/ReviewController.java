package com.cinebook.review.controller;

import com.cinebook.common.json.JsonLoaderService;
import com.cinebook.common.json.JsonLoaderService.ReviewJson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    @Autowired
    private JsonLoaderService jsonLoaderService;

    @GetMapping
    public ResponseEntity<List<ReviewJson>> getAllReviews() {
        List<ReviewJson> reviews = jsonLoaderService.getData("data/reviews.json", ReviewJson.class);
        return ResponseEntity.ok(reviews);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReviewJson> getReviewById(@PathVariable int id) {
        List<ReviewJson> reviews = jsonLoaderService.getData("data/reviews.json", ReviewJson.class);
        return reviews.stream()
                .filter(r -> r.id == id)
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Optional: filter by movieId
    @GetMapping("/movie/{movieId}")
    public ResponseEntity<List<ReviewJson>> getReviewsByMovieId(@PathVariable int movieId) {
        List<ReviewJson> reviews = jsonLoaderService.getData("data/reviews.json", ReviewJson.class);
        List<ReviewJson> filtered = reviews.stream()
                .filter(r -> r.movieId == movieId)
                .collect(Collectors.toList());
        return ResponseEntity.ok(filtered);
    }
}