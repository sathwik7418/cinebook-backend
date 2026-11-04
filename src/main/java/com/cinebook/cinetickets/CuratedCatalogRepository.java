package com.cinebook.cinetickets;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;

@Repository
public class CuratedCatalogRepository {
    private final CuratedCatalog catalog;

    public CuratedCatalogRepository(ObjectMapper mapper) {
        try (var input = new ClassPathResource("data/cinetickets-curated.json").getInputStream()) {
            catalog = mapper.readValue(input, CuratedCatalog.class);
        } catch (IOException e) {
            throw new IllegalStateException("CineTickets editorial collection could not be loaded.", e);
        }
    }

    public CuratedCatalog get() { return catalog; }
}
