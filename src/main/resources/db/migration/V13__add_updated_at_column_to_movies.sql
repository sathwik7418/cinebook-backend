-- V13: Add missing updated_at column to movies table
-- Movie.entity has @Column(name = "updated_at") on updatedAt field (LocalDate)
ALTER TABLE movies ADD COLUMN updated_at DATE;