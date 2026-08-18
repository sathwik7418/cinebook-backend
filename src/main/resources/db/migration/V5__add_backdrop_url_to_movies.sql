-- V5: Add missing backdrop_url column to movies table
-- The Movie.entity has @Column(length = 500) on backdropUrl field
ALTER TABLE movies ADD COLUMN backdrop_url VARCHAR(500);