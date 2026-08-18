-- V11: Add missing status column to movies table
-- Movie.entity has @Enumerated(EnumType.STRING) @Column(length = 20) on status field
ALTER TABLE movies ADD COLUMN status VARCHAR(20);