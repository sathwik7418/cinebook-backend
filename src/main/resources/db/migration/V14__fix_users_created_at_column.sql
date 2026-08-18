-- V14: Fix users.created_at column type from TIMESTAMP WITH TIME ZONE to DATE
-- User.entity has @Column(name = "created_at", nullable = false) on createdAt field (LocalDate)
ALTER TABLE users ALTER COLUMN created_at TYPE DATE USING created_at::date;