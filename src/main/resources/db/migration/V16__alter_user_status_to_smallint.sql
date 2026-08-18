-- V16: Change users.status column type from INTEGER to SMALLINT to match Hibernate enum mapping
-- User.entity has status field (enum) without @Column, defaults to ordinal stored as smallint
ALTER TABLE users ALTER COLUMN status TYPE SMALLINT USING status::SMALLINT;