package com.cinebook.cinetickets;

import java.util.Map;
import java.util.regex.Pattern;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cinetickets")
@Validated
public class CineTicketsController {
    private static final Pattern IMDB_ID = Pattern.compile("(?i)tt\\d{5,12}");
    private final CineTicketsService service;
    private final OmdbProperties properties;

    public CineTicketsController(CineTicketsService service, OmdbProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @GetMapping("/discover")
    public CineTicketsService.DiscoveryResponse discover() { return service.discover(); }

    @GetMapping("/collections")
    public java.util.List<CineTicketsService.CollectionSummary> collections() { return service.collections(); }

    @GetMapping("/collections/{slug}")
    public CineTicketsService.CollectionResponse collection(@PathVariable @Size(min = 2, max = 50) String slug) { return service.collection(slug); }

    @GetMapping("/movies/search")
    public OmdbClient.SearchPage search(@RequestParam @NotBlank @Size(min = 2, max = 80) String q,
                                        @RequestParam(defaultValue = "1") @Min(1) @Max(100) int page) {
        return service.search(q, page);
    }

    @GetMapping("/movies/by-title")
    public CineTicketsMovie byTitle(@RequestParam @NotBlank @Size(max = 100) String title,
                                   @RequestParam @Min(1888) @Max(2100) int year) {
        return service.movie(title, year);
    }

    @GetMapping("/movies/{imdbId}")
    public CineTicketsMovie movie(@PathVariable String imdbId) {
        if (!IMDB_ID.matcher(imdbId).matches()) throw new IllegalArgumentException("IMDb ID must use the tt followed by digits format.");
        return service.movie(imdbId);
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        boolean configured = properties.getApiKey() != null && !properties.getApiKey().isBlank();
        return Map.of("status", properties.isEnabled() && configured ? "CONFIGURED" : "DISABLED",
                "provider", "OMDB", "enabled", properties.isEnabled(), "configured", configured);
    }

    @ExceptionHandler(OmdbNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(OmdbNotFoundException ignored) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("code", "MOVIE_NOT_FOUND", "message", "No matching movie was found."));
    }

    @ExceptionHandler(OmdbUnavailableException.class)
    public ResponseEntity<Map<String, String>> unavailable(OmdbUnavailableException ignored) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("code", "MOVIE_PROVIDER_UNAVAILABLE", "message", "CineTickets movie discovery is temporarily unavailable."));
    }

    @ExceptionHandler(OmdbQueryException.class)
    public ResponseEntity<Map<String, String>> refineSearch(OmdbQueryException ignored) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("code", "SEARCH_TOO_BROAD", "message", "Please make your movie search more specific."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("code", "INVALID_REQUEST", "message", exception.getMessage()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> invalidParameters(ConstraintViolationException ignored) {
        return ResponseEntity.badRequest().body(Map.of("code", "INVALID_REQUEST", "message", "One or more request parameters are invalid."));
    }
}
