-- V6: Add missing certification column to movies table
-- Movie.entity has @Enumerated(EnumType.STRING) @Column(length = 10) on certification field
ALTER TABLE movies ADD COLUMN certification VARCHAR(10);