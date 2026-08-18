package com.cinebook.movie.service;

import com.cinebook.movie.model.Certification;
import com.cinebook.movie.model.Movie;
import com.cinebook.movie.model.MovieStatus;
import com.cinebook.movie.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MovieService {

    private final MovieRepository movieRepository;

    @Transactional(readOnly = true)
    public Page<Movie> findAll(Pageable pageable) {
        return movieRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<Movie> findAll() {
        return movieRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Movie> findById(Long id) {
        return movieRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Movie> findByTitleContaining(String title) {
        return movieRepository.findByTitleContaining(title);
    }

    @Transactional(readOnly = true)
    public List<Movie> findByGenre(String genre) {
        return movieRepository.findByGenre(genre);
    }

    @Transactional(readOnly = true)
    public List<Movie> findByLanguage(String language) {
        return movieRepository.findByLanguage(language);
    }

    @Transactional(readOnly = true)
    public List<Movie> findByCertification(Certification certification) {
        return movieRepository.findByCertification(certification);
    }

    @Transactional(readOnly = true)
    public List<Movie> findByRating(String rating) {
        return movieRepository.findByRating(rating);
    }

    @Transactional(readOnly = true)
    public List<Movie> findByStatus(MovieStatus status) {
        return movieRepository.findByStatus(status);
    }

    @Transactional(readOnly = true)
    public Page<Movie> findNowShowing(Pageable pageable) {
        return movieRepository.findByStatus(MovieStatus.NOW_SHOWING, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Movie> findComingSoon(Pageable pageable) {
        return movieRepository.findByStatus(MovieStatus.COMING_SOON, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Movie> findEnded(Pageable pageable) {
        return movieRepository.findByStatus(MovieStatus.ENDED, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Movie> searchMovies(String title, String genre, String language, Certification certification, String rating, MovieStatus status, Pageable pageable) {
        Specification<Movie> spec = Specification.where(null);
        if (title != null && !title.isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("title")), "%" + title.toLowerCase() + "%"));
        }
        if (genre != null && !genre.isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("genres")), "%" + genre.toLowerCase() + "%"));
        }
        if (language != null && !language.isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("languages")), "%" + language.toLowerCase() + "%"));
        }
        if (certification != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("certification"), certification));
        }
        if (rating != null && !rating.isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("rating"), rating));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        return movieRepository.findAll(spec, pageable);
    }

    @Transactional
    public Movie create(Movie movie) {
        if (movie.getStatus() == null) {
            movie.setStatus(MovieStatus.COMING_SOON);
        }
        return movieRepository.save(movie);
    }

    @Transactional
    public Movie update(Long id, Movie movieDetails) {
        return movieRepository.findById(id).map(movie -> {
            movie.setTitle(movieDetails.getTitle());
            movie.setDescription(movieDetails.getDescription());
            movie.setDuration(movieDetails.getDuration());
            movie.setReleaseDate(movieDetails.getReleaseDate());
            movie.setPosterUrl(movieDetails.getPosterUrl());
            movie.setBackdropUrl(movieDetails.getBackdropUrl());
            movie.setTrailerUrl(movieDetails.getTrailerUrl());
            movie.setRating(movieDetails.getRating());
            movie.setCertification(movieDetails.getCertification());
            movie.setStatus(movieDetails.getStatus());
            movie.setGenres(movieDetails.getGenres());
            movie.setLanguages(movieDetails.getLanguages());
            movie.setCast(movieDetails.getCast());
            movie.setUpdatedAt(LocalDate.now());
            return movieRepository.save(movie);
        }).orElseGet(() -> {
            movieDetails.setId(id);
            return movieRepository.save(movieDetails);
        });
    }

    @Transactional
    public void deleteById(Long id) {
        movieRepository.deleteById(id);
    }
}