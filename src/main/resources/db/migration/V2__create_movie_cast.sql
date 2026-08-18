-- V2: Create movie_cast table for Movie cast element collection
-- This table stores the cast/actor names associated with each movie
CREATE TABLE IF NOT EXISTS movie_cast (
    movie_id BIGINT NOT NULL,
    actor_name VARCHAR(255) NOT NULL,
    PRIMARY KEY (movie_id, actor_name),
    FOREIGN KEY (movie_id) REFERENCES movies(id) ON DELETE CASCADE
);