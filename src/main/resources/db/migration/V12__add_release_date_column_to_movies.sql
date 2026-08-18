-- V12: Add missing release_date column to movies table
-- Movie.entity has @Column(name = "release_date", nullable = false) on releaseDate field (LocalDate)
ALTER TABLE movies ADD COLUMN release_date DATE NOT NULL;