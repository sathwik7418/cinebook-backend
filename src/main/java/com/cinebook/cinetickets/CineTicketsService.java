package com.cinebook.cinetickets;

import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

@Service
public class CineTicketsService {
    private static final long DISCOVERY_TTL_MS = Duration.ofHours(6).toMillis();
    private final CuratedCatalog catalog;
    private final OmdbClient omdb;
    private final ExecutorService executor;
    private volatile CachedDiscovery discoveryCache;

    public CineTicketsService(CuratedCatalogRepository repository, OmdbClient omdb, ExecutorService cineTicketsExecutor) {
        this.catalog = repository.get();
        this.omdb = omdb;
        this.executor = cineTicketsExecutor;
    }

    public DiscoveryResponse discover() {
        CachedDiscovery cached = discoveryCache;
        if (cached != null && cached.expiresAt > System.currentTimeMillis()) return cached.value;
        synchronized (this) {
            cached = discoveryCache;
            if (cached != null && cached.expiresAt > System.currentTimeMillis()) return cached.value;
            List<String> ids = allIds();
            Map<String, CineTicketsMovie> resolved = resolve(ids);
            DiscoveryResponse result = assemble(resolved);
            discoveryCache = new CachedDiscovery(result, System.currentTimeMillis() + DISCOVERY_TTL_MS);
            return result;
        }
    }

    public List<CollectionSummary> collections() {
        return catalog.collections().stream().map(c -> new CollectionSummary(c.slug(), c.title(), c.description(), c.movieIds().size())).toList();
    }

    public CollectionResponse collection(String slug) {
        CuratedCatalog.CollectionDefinition definition = catalog.collections().stream()
                .filter(c -> c.slug().equalsIgnoreCase(slug)).findFirst().orElseThrow(OmdbNotFoundException::new);
        Map<String, CineTicketsMovie> resolved = resolve(definition.movieIds());
        return collectionResponse(definition, resolved);
    }

    public CineTicketsMovie movie(String imdbId) {
        if (imdbId == null || !imdbId.matches("(?i)tt\\d{5,12}")) throw new IllegalArgumentException("IMDb ID must use the tt followed by digits format.");
        return omdb.byImdbId(imdbId);
    }

    public CineTicketsMovie movie(String title, int year) {
        if (title == null || title.isBlank() || title.length() > 100 || year < 1888 || year > 2100) throw new IllegalArgumentException("A valid title and release year are required.");
        CineTicketsMovie result = omdb.byTitleAndYear(title.trim(), year);
        if (!isExpectedRecord(new CuratedCatalog.CuratedFilm(result.imdbId(), title.trim(), year), result)) throw new OmdbNotFoundException();
        return result;
    }

    public OmdbClient.SearchPage search(String query, int page) {
        if (query == null || query.trim().length() < 2 || query.length() > 80) throw new IllegalArgumentException("Search must contain 2 to 80 characters.");
        if (page < 1 || page > 100) throw new IllegalArgumentException("Page must be between 1 and 100.");
        return omdb.search(query.trim(), page);
    }

    private List<String> allIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        ids.addAll(catalog.hero());
        ids.addAll(catalog.featuredTop10());
        catalog.collections().forEach(c -> ids.addAll(c.movieIds()));
        return List.copyOf(ids);
    }

    private Map<String, CineTicketsMovie> resolve(List<String> ids) {
        Map<String, CuratedCatalog.CuratedFilm> verifiedCandidates = new LinkedHashMap<>();
        catalog.films().forEach(f -> verifiedCandidates.put(f.imdbId(), f));
        List<CompletableFuture<Resolved>> pending = ids.stream().distinct().map(id -> CompletableFuture.supplyAsync(() -> {
            try {
                CineTicketsMovie movie = omdb.byImdbId(id);
                CuratedCatalog.CuratedFilm candidate = verifiedCandidates.get(id);
                if (candidate != null && isExpectedRecord(candidate, movie)) return new Resolved(id, movie);
                return new Resolved(id, null);
            } catch (OmdbNotFoundException e) {
                return new Resolved(id, null);
            }
        }, executor)).toList();

        Map<String, CineTicketsMovie> result = new LinkedHashMap<>();
        try {
            CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).join();
            pending.stream().map(CompletableFuture::join).filter(r -> r.movie != null).forEach(r -> result.put(r.id, r.movie));
            return result;
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new OmdbUnavailableException("Movie provider is temporarily unavailable.");
        }
    }

    private DiscoveryResponse assemble(Map<String, CineTicketsMovie> movies) {
        List<CineTicketsMovie> hero = select(catalog.hero(), movies).stream().filter(CineTicketsMovie::artworkAvailable).toList();
        List<CineTicketsMovie> top = select(catalog.featuredTop10(), movies);
        List<CollectionResponse> collections = catalog.collections().stream().map(c -> collectionResponse(c, movies)).toList();
        List<CineTicketsMovie> all = select(catalog.films().stream().map(CuratedCatalog.CuratedFilm::imdbId).toList(), movies);
        return new DiscoveryResponse(hero, top, collections, all);
    }

    private static CollectionResponse collectionResponse(CuratedCatalog.CollectionDefinition definition, Map<String, CineTicketsMovie> movies) {
        return new CollectionResponse(definition.slug(), definition.title(), definition.description(), select(definition.movieIds(), movies));
    }

    private static List<CineTicketsMovie> select(List<String> ids, Map<String, CineTicketsMovie> movies) {
        LinkedHashSet<String> unique = new LinkedHashSet<>(ids);
        return unique.stream().map(movies::get).filter(java.util.Objects::nonNull).toList();
    }

    private static boolean isExpectedRecord(CuratedCatalog.CuratedFilm expected, CineTicketsMovie actual) {
        if (actual.title() == null || actual.year() == null || actual.year() != expected.year()) return false;
        return normalizeTitle(actual.title()).equals(normalizeTitle(expected.title()));
    }

    private static String normalizeTitle(String title) {
        return Normalizer.normalize(title, Normalizer.Form.NFKD).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private record Resolved(String id, CineTicketsMovie movie) {}
    private record CachedDiscovery(DiscoveryResponse value, long expiresAt) {}
    public record DiscoveryResponse(List<CineTicketsMovie> hero, List<CineTicketsMovie> featuredTop10,
                                    List<CollectionResponse> collections, List<CineTicketsMovie> allMovies) {}
    public record CollectionSummary(String slug, String title, String description, int candidateCount) {}
    public record CollectionResponse(String slug, String title, String description, List<CineTicketsMovie> movies) {}
}
