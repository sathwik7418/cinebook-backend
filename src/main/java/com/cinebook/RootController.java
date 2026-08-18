package com.cinebook;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RootController {

    @GetMapping("/")
    public String root() {
        return "CineBook API is running. Use /api/movies for movies endpoints.";
    }
}