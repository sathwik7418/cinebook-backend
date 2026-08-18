package com.cinebook.movie.service;

import com.cinebook.movie.dto.MovieRequest;
import com.cinebook.movie.model.Certification;
import com.cinebook.movie.model.Movie;
import com.cinebook.movie.model.MovieStatus;
import com.cinebook.movie.repository.MovieRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MovieServiceTest {

    @Mock
    private MovieRepository movieRepository;

    @InjectMocks
    private MovieService movieService;

    private Movie movie;

    @BeforeEach
    void setUp() {
        movie = new Movie();
        movie.setId(1L);
        movie.setTitle("Test Movie");
        movie.setDescription("Description");
        movie.setDuration(120);
        movie.setReleaseDate(LocalDate.of(2026, 1, 1));
        movie.setPosterUrl("poster.jpg");
        movie.setBackdropUrl("backdrop.jpg");
        movie.setTrailerUrl("trailer.jpg");
        movie.setRating("5");
        movie.setCertification(Certification.UA);
        movie.setStatus(MovieStatus.NOW_SHOWING);
        movie.setGenres(Collections.singleton("Action"));
        movie.setLanguages(Collections.singleton("English"));
        movie.setCast(Collections.singleton("Actor"));
        movie.setCreatedAt(LocalDate.now());
        movie.setUpdatedAt(LocalDate.now());
    }

    @Test
    void createMovie() {
        when(movieRepository.save(any(Movie.class))).thenReturn(movie);

        Movie saved = movieService.create(movie);

        assertNotNull(saved);
        assertEquals("Test Movie", saved.getTitle());
        verify(movieRepository, times(1)).save(any(Movie.class));
    }

    @Test
    void getMovieByIdFound() {
        when(movieRepository.findById(1L)).thenReturn(Optional.of(movie));

        Optional<Movie> found = movieService.findById(1L);

        assertTrue(found.isPresent());
        assertEquals("Test Movie", found.get().getTitle());
    }

    @Test
    void getMovieByIdNotFound() {
        when(movieRepository.findById(999L)).thenReturn(Optional.empty());

        Optional<Movie> found = movieService.findById(999L);

        assertFalse(found.isPresent());
    }

    @Test
    void updateMovie() {
        Movie existing = new Movie();
        existing.setId(1L);
        existing.setTitle("Old Title");
        existing.setDescription("Old Desc");
        existing.setDuration(100);
        existing.setReleaseDate(LocalDate.of(2025,1,1));
        existing.setPosterUrl("old.jpg");
        existing.setBackdropUrl("old.jpg");
        existing.setTrailerUrl("old.jpg");
        existing.setRating("3");
        existing.setCertification(Certification.UA);
        existing.setStatus(MovieStatus.ENDED);
        existing.setGenres(Collections.singleton("Drama"));
        existing.setLanguages(Collections.singleton("Spanish"));
        existing.setCast(Collections.singleton("Old Actor"));
        existing.setCreatedAt(LocalDate.of(2025,1,1));
        existing.setUpdatedAt(LocalDate.of(2025,1,1));

        MovieRequest request = new MovieRequest();
        request.setTitle("New Title");
        request.setDescription("New Desc");
        request.setDuration(130);
        request.setReleaseDate(LocalDate.of(2026,2,2));
        request.setPosterUrl("newposter.jpg");
        request.setBackdropUrl("newbackdrop.jpg");
        request.setTrailerUrl("newtrailer.jpg");
        request.setRating("4");
        request.setCertification(Certification.UA.name());
        request.setStatus(MovieStatus.NOW_SHOWING.name());
        request.setGenres(Collections.singleton("Comedy"));
        request.setLanguages(Collections.singleton("French"));
        request.setCast(Collections.singleton("New Actor"));

        when(movieRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(movieRepository.save(any(Movie.class))).thenAnswer(invocation -> {
            Movie arg = invocation.getArgument(0);
            arg.setId(1L);
            return arg;
        });

        Movie movieDetails = new Movie();
        movieDetails.setTitle(request.getTitle());
        movieDetails.setDescription(request.getDescription());
        movieDetails.setDuration(request.getDuration());
        movieDetails.setReleaseDate(request.getReleaseDate());
        movieDetails.setPosterUrl(request.getPosterUrl());
        movieDetails.setBackdropUrl(request.getBackdropUrl());
        movieDetails.setTrailerUrl(request.getTrailerUrl());
        movieDetails.setRating(request.getRating());
        movieDetails.setCertification(Certification.valueOf(request.getCertification()));
        movieDetails.setStatus(MovieStatus.valueOf(request.getStatus()));
        movieDetails.setGenres(request.getGenres());
        movieDetails.setLanguages(request.getLanguages());
        movieDetails.setCast(request.getCast());

        Movie updated = movieService.update(1L, movieDetails);

        assertNotNull(updated);
        assertEquals("New Title", updated.getTitle());
        assertEquals("New Desc", updated.getDescription());
        assertEquals(130, updated.getDuration());
        assertEquals(LocalDate.of(2026,2,2), updated.getReleaseDate());
        assertEquals("newposter.jpg", updated.getPosterUrl());
        assertEquals(Certification.UA, updated.getCertification());
        assertEquals(MovieStatus.NOW_SHOWING, updated.getStatus());
        verify(movieRepository, times(1)).save(any(Movie.class));
    }

    @Test
    void deleteMovie() {
        doNothing().when(movieRepository).deleteById(1L);
        movieService.deleteById(1L);
        verify(movieRepository, times(1)).deleteById(1L);
    }

    @Test
    void findNowShowing() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Movie> page = new PageImpl<>(Collections.singletonList(movie));
        when(movieRepository.findByStatus(MovieStatus.NOW_SHOWING, pageable)).thenReturn(page);
        Page<Movie> result = movieService.findNowShowing(pageable);
        assertFalse(result.isEmpty());
        assertEquals(1, result.getTotalElements());
        assertEquals(MovieStatus.NOW_SHOWING, result.getContent().get(0).getStatus());
    }
}
