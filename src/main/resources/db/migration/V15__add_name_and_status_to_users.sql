-- V15: Add missing name and status columns to users table
-- User.entity has @Column(name = "name", length = 100) on name field
-- User.entity has status field (no @Column, defaults to ordinal integer storage)
ALTER TABLE users ADD COLUMN name VARCHAR(100);
ALTER TABLE users ADD COLUMN status INTEGER;