package com.cinebook.cinetickets;

import com.cinebook.common.json.JsonLoaderService;
import com.cinebook.common.json.JsonLoaderService.MovieJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Stable, explicit booking identities for curated OMDb films, separate from legacy catalog IDs. */
@Component
public class CineTicketsBookingRegistry {
    private final Map<String, CineTicketsShowtimeService.BookingMapping> byImdbId;
    private final Map<Integer, CineTicketsShowtimeService.BookingMapping> byBookingId;

    public CineTicketsBookingRegistry(ObjectMapper mapper, CuratedCatalogRepository curated,
                                      JsonLoaderService legacyCatalog) {
        try (var input = new ClassPathResource("data/cinetickets-booking-mappings.json").getInputStream()) {
            CineTicketsShowtimeService.MappingFile file = mapper.readValue(input, CineTicketsShowtimeService.MappingFile.class);
            List<CineTicketsShowtimeService.BookingMapping> mappings = file.mappings();
            int maxLegacyId = legacyCatalog.getData("data/cinebook_movies.json", MovieJson.class).stream()
                    .mapToInt(movie -> movie.id).max().orElse(0);
            for (var mapping : mappings) {
                var editorial = curated.get().films().stream()
                        .filter(candidate -> candidate.imdbId().equalsIgnoreCase(mapping.imdbId()))
                        .findFirst().orElseThrow(() -> new IllegalStateException("Booking mapping is not a curated IMDb record."));
                if (!normalize(editorial.title()).equals(normalize(mapping.title())) || editorial.year() != mapping.year())
                    throw new IllegalStateException("Booking mapping title/year does not match its curated IMDb record.");
                if (mapping.bookingMovieId() <= maxLegacyId || mapping.bookingMovieId() >= 10_000)
                    throw new IllegalStateException("CineTickets booking IDs must be unique and fit the reserved show-ID field.");
                if (mapping.runtimeMinutes() < 1 || mapping.runtimeMinutes() > 300)
                    throw new IllegalStateException("CineTickets booking runtime is invalid.");
                if (!mapping.imdbId().matches("(?i)tt\\d{5,12}"))
                    throw new IllegalStateException("CineTickets booking mapping has an invalid IMDb ID.");
            }
            this.byImdbId = mappings.stream().collect(Collectors.toUnmodifiableMap(
                    mapping -> mapping.imdbId().toLowerCase(Locale.ROOT), Function.identity()));
            this.byBookingId = mappings.stream().collect(Collectors.toUnmodifiableMap(
                    CineTicketsShowtimeService.BookingMapping::bookingMovieId, Function.identity()));
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("CineTickets booking registry could not be loaded or validated.", exception);
        }
    }

    public CineTicketsShowtimeService.BookingMapping byImdbId(String imdbId) {
        return imdbId == null ? null : byImdbId.get(imdbId.toLowerCase(Locale.ROOT));
    }

    public CineTicketsShowtimeService.BookingMapping byBookingId(int bookingMovieId) {
        return byBookingId.get(bookingMovieId);
    }

    public List<CineTicketsShowtimeService.BookingMapping> all() { return List.copyOf(byBookingId.values()); }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKD).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
