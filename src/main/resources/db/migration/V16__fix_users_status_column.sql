-- V16: Fix users.status column type from SMALLINT to INTEGER
-- Hibernate validation found mismatch: expected INTEGER, found SMALLINT
ALTER TABLE users ALTER COLUMN status TYPE INTEGER USING status::integer;