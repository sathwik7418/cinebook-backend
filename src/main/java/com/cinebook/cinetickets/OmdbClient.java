package com.cinebook.cinetickets;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/** Server-side-only OMDb access. API credentials are never logged or returned. */
@Service
public class OmdbClient {
    private static final Logger log = LoggerFactory.getLogger(OmdbClient.class);
    private static final long DETAIL_TTL_MS = Duration.ofHours(24).toMillis();
    private static final long SEARCH_TTL_MS = Duration.ofMinutes(10).toMillis();
    private static final long REQUEST_INTERVAL_MS = 115;
    private static final DateTimeFormatter RELEASE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final RestTemplate restTemplate;
    private final OmdbProperties properties;
    private final Semaphore concurrency = new Semaphore(4);
    private final ConcurrentHashMap<String, CacheValue<CineTicketsMovie>> details = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CacheValue<CineTicketsMovie>> titleDetails = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CacheValue<SearchPage>> searches = new ConcurrentHashMap<>();
    private long lastRequestStarted;

    public OmdbClient(RestTemplate omdbRestTemplate, OmdbProperties properties) {
        this.restTemplate = omdbRestTemplate;
        this.properties = properties;
    }

    public CineTicketsMovie byImdbId(String imdbId) {
        String key = imdbId.toLowerCase(Locale.ROOT);
        CacheValue<CineTicketsMovie> cached = details.get(key);
        if (cached != null && cached.expiresAt > System.currentTimeMillis()) return cached.value;
        JsonNode body = request(Map.of("i", imdbId, "plot", "full"));
        if (isNotFound(body)) throw new OmdbNotFoundException();
        CineTicketsMovie movie = normalize(body);
        if (movie.imdbId() == null || !movie.imdbId().equalsIgnoreCase(imdbId)) {
            throw new OmdbUnavailableException("Movie provider returned an invalid record.");
        }
        store(details, key, movie, DETAIL_TTL_MS, 2048);
        return movie;
    }

    public CineTicketsMovie byTitleAndYear(String title, int year) {
        String key = title.toLowerCase(Locale.ROOT).trim() + "|" + year;
        CacheValue<CineTicketsMovie> cached = titleDetails.get(key);
        if (cached != null && cached.expiresAt > System.currentTimeMillis()) return cached.value;
        JsonNode body = request(Map.of("t", title, "y", year, "plot", "full"));
        if (isNotFound(body)) throw new OmdbNotFoundException();
        CineTicketsMovie movie = normalize(body);
        store(titleDetails, key, movie, DETAIL_TTL_MS, 512);
        if (movie.imdbId() != null) store(details, movie.imdbId().toLowerCase(Locale.ROOT), movie, DETAIL_TTL_MS, 2048);
        return movie;
    }

    public SearchPage search(String query, int page) {
        String key = query.toLowerCase(Locale.ROOT).trim() + "|" + page;
        CacheValue<SearchPage> cached = searches.get(key);
        if (cached != null && cached.expiresAt > System.currentTimeMillis()) return cached.value;
        JsonNode body = request(Map.of("s", query, "type", "movie", "page", page));
        if (isNotFound(body)) return new SearchPage(List.of(), 0, page);
        List<CineTicketsMovie> results = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        JsonNode search = body.path("Search");
        if (search.isArray()) {
            for (JsonNode result : search) {
                String id = text(result, "imdbID");
                String title = nullable(text(result, "Title"));
                if (id == null || title == null || !seenIds.add(id.toLowerCase(Locale.ROOT))) continue;
                String poster = poster(text(result, "Poster"));
                results.add(new CineTicketsMovie(id, id, title, title, parseYear(text(result, "Year")), null,
                        null, List.of(), null, null, List.of(), List.of(), List.of(), null, List.of(), poster,
                        "OMDB", poster != null));
            }
        }
        int total = integer(text(body, "totalResults"));
        SearchPage response = new SearchPage(results, total, page);
        store(searches, key, response, SEARCH_TTL_MS, 512);
        return response;
    }

    private JsonNode request(Map<String, ?> parameters) {
        if (!properties.isEnabled()) throw new OmdbUnavailableException("CineTickets movie provider is disabled.");
        String apiKey = properties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) throw new OmdbUnavailableException("CineTickets movie provider is not configured.");
        try {
            if (!"https".equalsIgnoreCase(URI.create(properties.getBaseUrl()).getScheme())) {
                throw new OmdbUnavailableException("Movie provider must be configured to use HTTPS.");
            }
        } catch (IllegalArgumentException e) {
            throw new OmdbUnavailableException("Movie provider URL is invalid.");
        }

        for (int attempt = 0; attempt < 3; attempt++) {
            boolean acquired = false;
            try {
                concurrency.acquire();
                acquired = true;
                throttle();
                UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(properties.getBaseUrl());
                parameters.forEach(builder::queryParam);
                URI uri = builder.queryParam("apikey", apiKey).build().encode().toUri();
                JsonNode response = restTemplate.getForObject(uri, JsonNode.class);
                if (response == null) throw new OmdbUnavailableException("Movie provider returned an empty response.");
                if (isProviderFailure(response)) throw new OmdbUnavailableException("Movie provider rejected the request.");
                return response;
            } catch (OmdbNotFoundException | OmdbUnavailableException e) {
                throw e;
            } catch (HttpStatusCodeException e) {
                if (e.getStatusCode().value() == 429 || e.getStatusCode().is5xxServerError()) {
                    if (attempt < 2) { pause(180L * (attempt + 1)); continue; }
                }
                log.warn("OMDb request failed with HTTP status {}.", e.getStatusCode().value());
                throw new OmdbUnavailableException("Movie provider is temporarily unavailable.");
            } catch (ResourceAccessException e) {
                if (attempt < 2) { pause(180L * (attempt + 1)); continue; }
                log.warn("OMDb request failed due to a connection or timeout.");
                throw new OmdbUnavailableException("Movie provider is temporarily unavailable.");
            } catch (RestClientException e) {
                log.warn("OMDb response could not be processed.");
                throw new OmdbUnavailableException("Movie provider returned an invalid response.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new OmdbUnavailableException("Movie provider request was interrupted.");
            } finally {
                if (acquired) concurrency.release();
            }
        }
        throw new OmdbUnavailableException("Movie provider is temporarily unavailable.");
    }

    private synchronized void throttle() throws InterruptedException {
        long wait = lastRequestStarted + REQUEST_INTERVAL_MS - System.currentTimeMillis();
        if (wait > 0) Thread.sleep(wait);
        lastRequestStarted = System.currentTimeMillis();
    }

    private static void pause(long millis) {
        try { Thread.sleep(millis); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static boolean isProviderFailure(JsonNode body) {
        if (!"False".equalsIgnoreCase(text(body, "Response"))) return false;
        String error = text(body, "Error");
        if (error != null && error.toLowerCase(Locale.ROOT).contains("too many results")) throw new OmdbQueryException();
        return error != null && !error.toLowerCase(Locale.ROOT).contains("not found");
    }

    private static boolean isNotFound(JsonNode body) {
        String error = text(body, "Error");
        return "False".equalsIgnoreCase(text(body, "Response")) && error != null && error.toLowerCase(Locale.ROOT).contains("not found");
    }

    private static CineTicketsMovie normalize(JsonNode node) {
        String imdbId = nullable(text(node, "imdbID"));
        String title = nullable(text(node, "Title"));
        String poster = poster(text(node, "Poster"));
        String rating = nullable(text(node, "imdbRating"));
        String released = nullable(text(node, "Released"));
        return new CineTicketsMovie(imdbId, imdbId, title, null, parseYear(text(node, "Year")), parseRelease(released),
                nullable(text(node, "Plot")), split(text(node, "Genre")), parseRuntime(text(node, "Runtime")),
                nullable(text(node, "Director")), split(text(node, "Actors")), split(text(node, "Language")),
                split(text(node, "Country")), decimal(rating), parseRatings(node.path("Ratings")), poster,
                "OMDB", poster != null);
    }

    private static List<CineTicketsMovie.Rating> parseRatings(JsonNode node) {
        List<CineTicketsMovie.Rating> values = new ArrayList<>();
        if (node.isArray()) for (JsonNode item : node) {
            String source = nullable(text(item, "Source"));
            String value = nullable(text(item, "Value"));
            if (source != null && value != null) values.add(new CineTicketsMovie.Rating(source, value));
        }
        return List.copyOf(values);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String nullable(String value) { return value == null || value.isBlank() || "N/A".equalsIgnoreCase(value.trim()) ? null : value.trim(); }
    private static List<String> split(String value) {
        String clean = nullable(value);
        if (clean == null) return List.of();
        return java.util.Arrays.stream(clean.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
    }
    private static Integer parseYear(String value) {
        String clean = nullable(value);
        if (clean == null || clean.length() < 4) return null;
        try { return Integer.parseInt(clean.substring(0, 4)); } catch (NumberFormatException e) { return null; }
    }
    private static Integer parseRuntime(String value) {
        String clean = nullable(value);
        if (clean == null) return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)").matcher(clean);
        if (!matcher.find()) return null;
        try { return Integer.parseInt(matcher.group(1)); } catch (NumberFormatException e) { return null; }
    }
    private static BigDecimal decimal(String value) {
        String clean = nullable(value);
        if (clean == null) return null;
        try { return new BigDecimal(clean); } catch (NumberFormatException e) { return null; }
    }
    private static LocalDate parseRelease(String value) {
        String clean = nullable(value);
        if (clean == null) return null;
        try { return LocalDate.parse(clean, RELEASE_FORMAT); } catch (DateTimeParseException e) { return null; }
    }
    private static String poster(String value) {
        String clean = nullable(value);
        if (clean == null) return null;
        try {
            URI uri = URI.create(clean);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null) return null;
            return "http".equalsIgnoreCase(uri.getScheme()) ? "https" + clean.substring(4) : clean;
        } catch (IllegalArgumentException e) { return null; }
    }
    private static int integer(String value) {
        String clean = nullable(value);
        if (clean == null) return 0;
        try { return Integer.parseInt(clean); } catch (NumberFormatException e) { return 0; }
    }
    private static <T> void store(ConcurrentHashMap<String, CacheValue<T>> cache, String key, T value, long ttl, int maxEntries) {
        long now = System.currentTimeMillis();
        if (cache.size() >= maxEntries) {
            cache.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
            if (cache.size() >= maxEntries) cache.keySet().stream().findFirst().ifPresent(cache::remove);
        }
        cache.put(key, new CacheValue<>(value, now + ttl));
    }
    private record CacheValue<T>(T value, long expiresAt) {}
    public record SearchPage(List<CineTicketsMovie> movies, int totalResults, int page) {}
}
