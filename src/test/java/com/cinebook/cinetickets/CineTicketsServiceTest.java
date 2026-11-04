package com.cinebook.cinetickets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CineTicketsServiceTest {
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void stopExecutor() { executor.shutdownNow(); }

    @Test
    void titleYearLookupRejectsAnUnexpectedProviderMatch() {
        OmdbClient provider = mock(OmdbClient.class);
        CineTicketsMovie inception = new CineTicketsMovie("tt1375666", "tt1375666", "Inception", null, 2010, null,
                null, List.of(), null, null, List.of(), List.of(), List.of(), new BigDecimal("8.8"), List.of(), null, "OMDB", false);
        when(provider.byTitleAndYear("Inception", 2011)).thenReturn(inception);
        CineTicketsService service = new CineTicketsService(new CuratedCatalogRepository(new ObjectMapper()), provider, executor);
        assertThrows(OmdbNotFoundException.class, () -> service.movie("Inception", 2011));
    }

    @Test
    void collectionMetadataDoesNotResolveOrMutateLegacyBookingRecords() {
        OmdbClient provider = mock(OmdbClient.class);
        CineTicketsService service = new CineTicketsService(new CuratedCatalogRepository(new ObjectMapper()), provider, executor);
        var collections = service.collections();
        assertEquals(6, collections.size());
        assertTrue(collections.stream().anyMatch(collection -> collection.slug().equals("all-curated")));
        verifyNoInteractions(provider);
    }

    @Test
    void discoveryVerifiesCuratedTitleAndYearAndCachesTheCollection() {
        CuratedCatalog catalog = new CuratedCatalogRepository(new ObjectMapper()).get();
        Map<String, CuratedCatalog.CuratedFilm> films = catalog.films().stream()
                .collect(java.util.stream.Collectors.toMap(CuratedCatalog.CuratedFilm::imdbId, film -> film));
        OmdbClient provider = mock(OmdbClient.class);
        when(provider.byImdbId(anyString())).thenAnswer(invocation -> {
            CuratedCatalog.CuratedFilm film = films.get(invocation.getArgument(0));
            return new CineTicketsMovie(film.imdbId(), film.imdbId(), film.title(), null, film.year(), LocalDate.of(film.year(), 1, 1),
                    "A real provider plot.", List.of("Drama"), 120, "A Director", List.of("An Actor"), List.of("English"),
                    List.of("USA"), new BigDecimal("8.0"), List.of(), "https://m.media-amazon.com/poster.jpg", "OMDB", true);
        });
        CineTicketsService service = new CineTicketsService(new CuratedCatalogRepository(new ObjectMapper()), provider, executor);

        var first = service.discover();
        var cached = service.discover();

        assertSame(first, cached);
        assertEquals(5, first.hero().size());
        assertEquals(10, first.featuredTop10().size());
        assertEquals(38, first.allMovies().size());
        assertEquals(38, mockingDetails(provider).getInvocations().stream().filter(call -> call.getMethod().getName().equals("byImdbId")).count());
    }
}
