package com.cinebook.cinetickets;

import java.util.List;

public record CuratedCatalog(List<CuratedFilm> films, List<String> hero, List<String> featuredTop10, List<CollectionDefinition> collections) {
    public record CuratedFilm(String imdbId, String title, int year) {}
    public record CollectionDefinition(String slug, String title, String description, List<String> movieIds) {}
}
