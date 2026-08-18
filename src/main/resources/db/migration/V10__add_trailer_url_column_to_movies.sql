-- V10: Add missing trailer_url column to movies table
-- Movie.entity has @Column(length = 500) on trailerUrl field
ALTER TABLE movies ADD COLUMN trailer_url VARCHAR(500);