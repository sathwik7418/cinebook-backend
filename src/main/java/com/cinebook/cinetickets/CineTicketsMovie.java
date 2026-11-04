package com.cinebook.cinetickets;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CineTicketsMovie(
        String externalId,
        String imdbId,
        String title,
        String originalTitle,
        Integer year,
        LocalDate releasedDate,
        String plot,
        List<String> genres,
        Integer runtimeMinutes,
        String director,
        List<String> cast,
        List<String> language,
        List<String> country,
        BigDecimal imdbRating,
        List<Rating> ratings,
        String posterUrl,
        String source,
        boolean artworkAvailable) {

    public record Rating(String source, String value) {}
}
