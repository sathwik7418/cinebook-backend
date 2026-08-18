package com.cinebook.movie.repository;

import com.cinebook.movie.model.Certification;
import com.cinebook.movie.model.Movie;
import com.cinebook.movie.model.MovieStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface MovieRepository extends JpaRepository<Movie, Long>, JpaSpecificationExecutor<Movie> {

    @Query("SELECT m FROM Movie m WHERE m.title LIKE %:title%")
    List<Movie> findByTitleContaining(@Param("title") String title);

    @Query("SELECT m FROM Movie m WHERE m.genres LIKE %:genre%")
    List<Movie> findByGenre(@Param("genre") String genre);

    @Query("SELECT m FROM Movie m WHERE m.languages LIKE %:language%")
    List<Movie> findByLanguage(@Param("language") String language);

    @Query("SELECT m FROM Movie m WHERE m.certification = :certification")
    List<Movie> findByCertification(@Param("certification") Certification certification);

    @Query("SELECT m FROM Movie m WHERE m.rating = :rating")
    List<Movie> findByRating(@Param("rating") String rating);

    @Query("SELECT m FROM Movie m WHERE m.status = :status")
    List<Movie> findByStatus(@Param("status") MovieStatus status);

    // New pagination methods
    Page<Movie> findByTitleContaining(String title, Pageable pageable);
    Page<Movie> findByGenresContaining(String genre, Pageable pageable);
    Page<Movie> findByLanguagesContaining(String language, Pageable pageable);
    Page<Movie> findByCertification(Certification certification, Pageable pageable);
    Page<Movie> findByRating(String rating, Pageable pageable);
    Page<Movie> findByStatus(MovieStatus status, Pageable pageable);
}