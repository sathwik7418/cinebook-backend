-- V4: Create movie_languages table for Movie languages element collection
-- This table stores the languages associated with each movie
CREATE TABLE IF NOT EXISTS movie_languages (
    movie_id BIGINT NOT NULL,
    language VARCHAR(255) NOT NULL,
    PRIMARY KEY (movie_id, language),
    FOREIGN KEY (movie_id) REFERENCES movies(id) ON DELETE CASCADE
);