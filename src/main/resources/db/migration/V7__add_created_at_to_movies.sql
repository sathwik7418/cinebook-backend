-- V7: Add missing created_at column to movies table
-- Movie.entity has @Column(name = "created_at", updatable = false) on createdAt field (LocalDate)
ALTER TABLE movies ADD COLUMN created_at DATE;