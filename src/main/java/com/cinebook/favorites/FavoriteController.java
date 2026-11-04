package com.cinebook.favorites;

import com.cinebook.common.json.JsonFileStore;
import com.cinebook.common.json.JsonLoaderService;
import com.cinebook.user.model.JsonUserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {
    private final JsonFileStore store; private final JsonLoaderService catalog;
    public FavoriteController(JsonFileStore store, JsonLoaderService catalog) { this.store = store; this.catalog = catalog; }

    @GetMapping public List<Integer> list(@AuthenticationPrincipal JsonUserPrincipal user) {
        return store.readList("favorites.json", Favorite.class).stream().filter(item -> item.userId == user.getId())
                .map(item -> item.movieId).sorted().toList();
    }

    @PostMapping("/{movieId}") @ResponseStatus(HttpStatus.CREATED)
    public List<Integer> add(@PathVariable int movieId, @AuthenticationPrincipal JsonUserPrincipal user) {
        boolean exists = catalog.getData("data/cinebook_movies.json", JsonLoaderService.MovieJson.class)
                .stream().anyMatch(movie -> movie.id == movieId);
        if (!exists) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Movie not found.");
        synchronized (store) {
            List<Favorite> all = store.readList("favorites.json", Favorite.class);
            if (all.stream().noneMatch(item -> item.userId == user.getId() && item.movieId == movieId)) {
                Favorite favorite = new Favorite(); favorite.userId = user.getId(); favorite.movieId = movieId;
                favorite.createdAt = Instant.now().toString(); all.add(favorite); store.writeList("favorites.json", all);
            }
        }
        return list(user);
    }

    @DeleteMapping("/{movieId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable int movieId, @AuthenticationPrincipal JsonUserPrincipal user) {
        synchronized (store) {
            List<Favorite> all = store.readList("favorites.json", Favorite.class);
            all.removeIf(item -> item.userId == user.getId() && item.movieId == movieId);
            store.writeList("favorites.json", all);
        }
    }
    public static class Favorite { public long userId; public int movieId; public String createdAt; }
}
