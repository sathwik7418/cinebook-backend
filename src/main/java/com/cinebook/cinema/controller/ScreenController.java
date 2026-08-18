package com.cinebook.cinema.controller;

import com.cinebook.common.json.JsonLoaderService;
import com.cinebook.common.json.JsonLoaderService.CinemaJson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/screens")
public class ScreenController {

    @Autowired
    private JsonLoaderService jsonLoaderService;

    // Inner class to match frontend Screen type
    public static class Screen {
        public int id;
        public int theaterId;
        public String name;
        public int capacity;
        public String screenType;
        public String status;
    }

    @GetMapping
    public ResponseEntity<List<Screen>> getAllScreens() {
        List<CinemaJson> cinemas = jsonLoaderService.getData("data/cinemas.json", CinemaJson.class);
        List<Screen> screens = new ArrayList<>();
        int id = 1;
        int theaterId = 1; // we assume each cinema is a theater for simplicity
        for (CinemaJson cinema : cinemas) {
            int screenCount = cinema.screens;
            for (int i = 1; i <= screenCount; i++) {
                Screen screen = new Screen();
                screen.id = id++;
                screen.theaterId = theaterId;
                screen.name = "Screen " + i;
                screen.capacity = 100 + (i * 20); // dummy capacity
                screen.screenType = "Standard"; // dummy screen type
                screen.status = "ACTIVE"; // dummy status
                screens.add(screen);
            }
            theaterId++;
        }
        return ResponseEntity.ok(screens);
    }

    @GetMapping("/theater/{theaterId}")
    public ResponseEntity<List<Screen>> getScreensByTheaterId(@PathVariable int theaterId) {
        List<CinemaJson> cinemas = jsonLoaderService.getData("data/cinemas.json", CinemaJson.class);
        List<Screen> screens = new ArrayList<>();
        int id = 1;
        int currentTheaterId = 1;
        for (CinemaJson cinema : cinemas) {
            if (currentTheaterId == theaterId) {
                int screenCount = cinema.screens;
                for (int i = 1; i <= screenCount; i++) {
                    Screen screen = new Screen();
                    screen.id = id++;
                    screen.theaterId = theaterId;
                    screen.name = "Screen " + i;
                    screen.capacity = 100 + (i * 20);
                    screen.screenType = "Standard";
                    screen.status = "ACTIVE";
                    screens.add(screen);
                }
                break;
            }
            currentTheaterId++;
            id += cinema.screens;
        }
        if (screens.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(screens);
    }
}