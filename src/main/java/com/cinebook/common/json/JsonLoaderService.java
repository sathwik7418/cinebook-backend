package com.cinebook.common.json;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to load JSON files from classpath into memory and provide read-only access.
 */
@Service
public class JsonLoaderService {

    private final ObjectMapper objectMapper = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final Map<String, Object> dataMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void loadAll() {
        // List of JSON files to load
        String[] jsonFiles = {
                "data/cinebook_movies.json",
                "data/cinebook_collections.json",
                "data/cinemas.json",
                "data/shows.json",
                "data/genres.json",
                "data/actors.json",
                "data/directors.json",
                "data/reviews.json",
                "data/users.json"
        };

        for (String fileName : jsonFiles) {
            try {
                ClassPathResource resource = new ClassPathResource(fileName);
                try (InputStream inputStream = resource.getInputStream()) {
                    // Determine type based on file name
                    Object data = readJson(inputStream, fileName);
                    dataMap.put(fileName, data);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to load JSON file: " + fileName, e);
            }
        }
    }

    private Object readJson(InputStream inputStream, String fileName) throws IOException {
        if (fileName.contains("movies")) {
            return objectMapper.readValue(inputStream, new TypeReference<List<MovieJson>>() {});
        } else if (fileName.contains("collections")) {
            return objectMapper.readValue(inputStream, new TypeReference<List<CollectionJson>>() {});
        } else if (fileName.contains("cinemas")) {
            return objectMapper.readValue(inputStream, new TypeReference<List<CinemaJson>>() {});
        } else if (fileName.contains("shows")) {
            return objectMapper.readValue(inputStream, new TypeReference<List<ShowJson>>() {});
        } else if (fileName.contains("genres")) {
            return objectMapper.readValue(inputStream, new TypeReference<List<GenreJson>>() {});
        } else if (fileName.contains("actors")) {
            return objectMapper.readValue(inputStream, new TypeReference<List<ActorJson>>() {});
        } else if (fileName.contains("directors")) {
            return objectMapper.readValue(inputStream, new TypeReference<List<DirectorJson>>() {});
        } else if (fileName.contains("reviews")) {
            return objectMapper.readValue(inputStream, new TypeReference<List<ReviewJson>>() {});
        } else if (fileName.contains("users")) {
            return objectMapper.readValue(inputStream, new TypeReference<List<UserJson>>() {});
        } else {
            // fallback to generic List of Map
            return objectMapper.readValue(inputStream, new TypeReference<List<Map<String, Object>>>() {});
        }
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> getData(String fileName, Class<T> type) {
        Object data = dataMap.get(fileName);
        if (data == null) {
            throw new IllegalArgumentException("No data loaded for file: " + fileName);
        }
        // Assuming data is List<T>
        return (List<T>) data;
    }

    // Inner classes representing JSON structure (simplified)
    public static class MovieJson {
        public int id;
        public String title;
        public String description;
        public String poster;
        public String backdrop;
        public String trailerUrl;
        public String language;
        public List<String> genres;
        public int duration;
        public String certification;
        public Double rating;
        public String releaseDate;
        public String status;
        public String director;
        public List<String> cast;
        public List<String> tags;
    }

    public static class CollectionJson {
        public String id;
        public String name;
        public String description;
        public List<Integer> movieIds;
    }

    public static class CinemaJson {
        public int id;
        public String name;
        public String location;
        public String city;
        public String address;
        public int screens;
        public List<String> facilities;
        public String state;
    }

    public static class ShowJson {
        public int id;
        public int movieId;
        public int cinemaId;
        public String screen;
        public String format;
        public String date;
        public String startTime;
        public String endTime;
        public int price;
        public int availableSeats;
    }

    public static class GenreJson {
        public int id;
        public String name;
        public String description;
    }

    public static class ActorJson {
        public int id;
        public String name;
        public String bio;
        public String birthDate;
        public List<String> knownFor;
    }

    public static class DirectorJson {
        public int id;
        public String name;
        public String bio;
        public String birthDate;
        public List<String> knownFor;
    }

    public static class ReviewJson {
        public int id;
        public int movieId;
        public int userId;
        public int rating;
        public String comment;
        public String date;
    }

    public static class UserJson {
        public int id;
        public String username;
        public String email;
        public String firstName;
        public String lastName;
        public String role;
    }
}