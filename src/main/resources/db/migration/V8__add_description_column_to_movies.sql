-- V8: Add missing description column to movies table
-- Movie.entity has @Column(name = "description", length = 500) field
ALTER TABLE movies ADD COLUMN description VARCHAR(500);