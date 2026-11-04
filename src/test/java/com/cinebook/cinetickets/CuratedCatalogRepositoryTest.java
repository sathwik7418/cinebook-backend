package com.cinebook.cinetickets;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CuratedCatalogRepositoryTest {
    @Test
    void editorialCatalogUsesUniqueExternalIdsAndHasBalancedCollections() {
        CuratedCatalog catalog = new CuratedCatalogRepository(new ObjectMapper()).get();
        assertEquals(38, catalog.films().size());
        assertEquals(5, catalog.hero().size());
        assertEquals(10, catalog.featuredTop10().size());
        assertEquals(catalog.films().size(), new HashSet<>(catalog.films().stream().map(CuratedCatalog.CuratedFilm::imdbId).toList()).size());
        assertTrue(catalog.films().stream().allMatch(film -> film.imdbId().matches("tt\\d{5,12}") && film.year() >= 1888));
        assertTrue(catalog.collections().stream().anyMatch(collection -> collection.slug().equals("indian-cinema")));
        assertTrue(catalog.collections().stream().anyMatch(collection -> collection.slug().equals("christopher-nolan")));
    }

    @Test
    void normalizedDiscoveryModelKeepsLegacyBookingIdsSeparate() {
        assertFalse(java.util.Arrays.stream(CineTicketsMovie.class.getRecordComponents()).anyMatch(component -> component.getName().equals("id")));
        assertTrue(java.util.Arrays.stream(CineTicketsMovie.class.getRecordComponents()).anyMatch(component -> component.getName().equals("imdbId")));
    }
}
