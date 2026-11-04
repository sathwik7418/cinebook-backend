package com.cinebook.common.json;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class JsonLoaderService {

    private static final String AUTH_USERS_FILE = "data/auth-users.json";

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(
                    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    false
            );

    private final Map<String, Object> dataMap =
            new ConcurrentHashMap<>();

    @Value("${cinebook.storage.directory:./data}")
    private String storageDirectory = "./data";

    @PostConstruct
    public void loadAll() {

        String[] jsonFiles = {
                "data/cinebook_movies.json",
                "data/cinebook_collections.json",
                "data/cinemas.json",
                "data/genres.json",
                "data/actors.json",
                "data/directors.json",
                "data/reviews.json",
                "data/users.json",
                "data/auth-users.json"
        };

        for (String fileName : jsonFiles) {

            try {
                ClassPathResource resource =
                        new ClassPathResource(fileName);

                try (InputStream inputStream =
                             resource.getInputStream()) {

                    Object data =
                            readJson(inputStream, fileName);

                    dataMap.put(fileName, data);
                }

            } catch (IOException e) {

                throw new RuntimeException(
                        "Failed to load JSON file: " + fileName,
                        e
                );
            }
        }

        // Mutable account state lives outside the packaged classpath. Bootstrap
        // copies the existing catalog account file once and never overwrites it.
        Path authStore = authUsersPath();
        try {
            Files.createDirectories(authStore.getParent());
            if (Files.exists(authStore)) {
                List<AuthUserJson> users = objectMapper.readValue(authStore.toFile(),
                        new TypeReference<List<AuthUserJson>>() {});
                dataMap.put(AUTH_USERS_FILE, users);
            } else {
                persistAuthUsers(getData(AUTH_USERS_FILE, AuthUserJson.class));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize persistent account store.", e);
        }
    }

    private Object readJson(
            InputStream inputStream,
            String fileName
    ) throws IOException {

        if (fileName.contains("movies")) {

            return objectMapper.readValue(
                    inputStream,
                    new TypeReference<List<MovieJson>>() {}
            );

        } else if (fileName.contains("collections")) {

            return objectMapper.readValue(
                    inputStream,
                    new TypeReference<List<CollectionJson>>() {}
            );

        } else if (fileName.contains("cinemas")) {

            return objectMapper.readValue(
                    inputStream,
                    new TypeReference<List<CinemaJson>>() {}
            );

        } else if (fileName.contains("shows")) {

            return objectMapper.readValue(
                    inputStream,
                    new TypeReference<List<ShowJson>>() {}
            );

        } else if (fileName.contains("genres")) {

            return objectMapper.readValue(
                    inputStream,
                    new TypeReference<List<GenreJson>>() {}
            );

        } else if (fileName.contains("actors")) {

            return objectMapper.readValue(
                    inputStream,
                    new TypeReference<List<ActorJson>>() {}
            );

        } else if (fileName.contains("directors")) {

            return objectMapper.readValue(
                    inputStream,
                    new TypeReference<List<DirectorJson>>() {}
            );

        } else if (fileName.contains("reviews")) {

            return objectMapper.readValue(
                    inputStream,
                    new TypeReference<List<ReviewJson>>() {}
            );

        } else if (fileName.equals("data/users.json")) {

            return objectMapper.readValue(
                    inputStream,
                    new TypeReference<List<UserJson>>() {}
            );

        } else if (fileName.equals(AUTH_USERS_FILE)) {

            return objectMapper.readValue(
                    inputStream,
                    new TypeReference<List<AuthUserJson>>() {}
            );

        } else {

            return objectMapper.readValue(
                    inputStream,
                    new TypeReference<List<Map<String, Object>>>() {}
            );
        }
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> getData(
            String fileName,
            Class<T> type
    ) {

        Object data = dataMap.get(fileName);

        if (data == null) {
            throw new IllegalArgumentException(
                    "No data loaded for file: " + fileName
            );
        }

        return (List<T>) data;
    }

    /**
     * Adds a new authentication user and persists it
     * to src/main/resources/data/auth-users.json.
     *
     * This is intended for the current local JSON-backed
     * development setup.
     */
    public synchronized AuthUserJson addAuthUser(
            AuthUserJson newUser
    ) {

        List<AuthUserJson> currentUsers =
                getData(
                        AUTH_USERS_FILE,
                        AuthUserJson.class
                );

        List<AuthUserJson> updatedUsers =
                new ArrayList<>(currentUsers);

        updatedUsers.add(newUser);

        try {
            persistAuthUsers(updatedUsers);

            // Keep the in-memory copy synchronized.
            dataMap.put(
                    AUTH_USERS_FILE,
                    updatedUsers
            );

            return newUser;

        } catch (IOException e) {

            throw new RuntimeException(
                    "Failed to save user to auth-users.json",
                    e
            );
        }
    }

    public synchronized AuthUserJson updateAuthUser(AuthUserJson updatedUser) {
        List<AuthUserJson> users = new ArrayList<>(getData(AUTH_USERS_FILE, AuthUserJson.class));
        int index = -1;
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).id == updatedUser.id) { index = i; break; }
        }
        if (index < 0) throw new IllegalArgumentException("Account not found.");
        users.set(index, updatedUser);
        try {
            persistAuthUsers(users);
            dataMap.put(AUTH_USERS_FILE, users);
            return updatedUser;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to update account.", e);
        }
    }

    private Path authUsersPath() {
        return Paths.get(storageDirectory).resolve(AUTH_USERS_FILE);
    }

    private void persistAuthUsers(List<AuthUserJson> users) throws IOException {
        Path target = authUsersPath();
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), users);
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException unsupportedAtomicMove) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Public user profile JSON.
     */
    public static class UserJson {

        public int id;

        public String username;

        public String email;

        public String avatar;

        public List<String> favoriteGenres;

        public List<String> favoriteLanguages;
    }

    /**
     * Authentication user JSON.
     */
    public static class AuthUserJson {

        public int id;

        public String username;

        public String email;

        public String passwordHash;

        public String role;

        public String name;

        public Boolean emailVerified;

        public String createdAt;

        public String preferredCity;

        public String googleSubject;

        public Integer tokenVersion = 0;

        public List<String> linkedProviders = new ArrayList<>(List.of("password"));

        public Map<String, Boolean> notificationPreferences = new ConcurrentHashMap<>();
    }

    public static class MovieJson {

        public int id;

        public String title;

        /** OMDb identifier for CineTickets-only booking identities; null for legacy catalog movies. */
        public String imdbId;

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

        public List<String> formats;

        public String state;
    }

    public static class ShowJson {

        public long id;

        public int movieId;

        /** Human-readable snapshot for booking history; absent in old schedule files. */
        public String movieTitle;

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
}
