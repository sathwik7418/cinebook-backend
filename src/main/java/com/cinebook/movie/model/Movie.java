package com.cinebook.movie.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Movie {

    private Long id;

    private String title;

    private String description;

    private Integer duration;

    private LocalDate releaseDate;

    @JsonProperty("poster")
    private String posterUrl;

    @JsonProperty("backdrop")
    private String backdropUrl;

    private String trailerUrl;

    private String rating;

    private Certification certification;

    private MovieStatus status;

    private LocalDate createdAt;

    private LocalDate updatedAt;

    private List<String> genres;

    private List<String> languages;

    private List<String> cast;
}