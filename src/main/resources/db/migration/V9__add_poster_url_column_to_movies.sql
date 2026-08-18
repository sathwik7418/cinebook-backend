-- V9: Add missing poster_url column to movies table
-- Movie.entity has @Column(length = 500) on posterUrl field
ALTER TABLE movies ADD COLUMN poster_url VARCHAR(500);