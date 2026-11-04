CREATE TABLE IF NOT EXISTS social_accounts (
    id INTEGER PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    bio VARCHAR(280) NOT NULL DEFAULT '',
    avatar_reference VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_social_accounts_username_lower ON social_accounts (lower(username));

CREATE TABLE IF NOT EXISTS cine_posts (
    id UUID PRIMARY KEY,
    author_id INTEGER NOT NULL REFERENCES social_accounts(id),
    content VARCHAR(500) NOT NULL DEFAULT '',
    visibility VARCHAR(20) NOT NULL CHECK (visibility IN ('PUBLIC','FOLLOWERS_ONLY','PRIVATE')),
    spoiler_flag BOOLEAN NOT NULL DEFAULT false,
    post_type VARCHAR(20) NOT NULL CHECK (post_type IN ('DISCUSSION','REVIEW','POLL','IMAGE')),
    quoted_post_id UUID REFERENCES cine_posts(id),
    movie_id INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    CHECK (char_length(content) <= 500)
);
CREATE INDEX IF NOT EXISTS ix_cine_posts_latest ON cine_posts (created_at DESC, id DESC) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_cine_posts_author_latest ON cine_posts (author_id, created_at DESC, id DESC) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_cine_posts_movie_latest ON cine_posts (movie_id, created_at DESC, id DESC) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS post_media (
    id UUID PRIMARY KEY,
    post_id UUID REFERENCES cine_posts(id) ON DELETE SET NULL,
    owner_id INTEGER NOT NULL REFERENCES social_accounts(id),
    media_type VARCHAR(40) NOT NULL,
    storage_reference VARCHAR(255) NOT NULL UNIQUE,
    position SMALLINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_post_media_post ON post_media (post_id, position);

CREATE TABLE IF NOT EXISTS post_reactions (
    post_id UUID NOT NULL REFERENCES cine_posts(id),
    user_id INTEGER NOT NULL REFERENCES social_accounts(id),
    reaction_type VARCHAR(20) NOT NULL DEFAULT 'LIKE' CHECK (reaction_type IN ('LIKE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (post_id, user_id)
);
CREATE INDEX IF NOT EXISTS ix_post_reactions_user ON post_reactions (user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS post_comments (
    id UUID PRIMARY KEY,
    post_id UUID NOT NULL REFERENCES cine_posts(id),
    author_id INTEGER NOT NULL REFERENCES social_accounts(id),
    parent_comment_id UUID,
    depth SMALLINT NOT NULL DEFAULT 0 CHECK (depth IN (0,1)),
    content VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    CHECK (char_length(content) BETWEEN 1 AND 1000),
    UNIQUE (id, post_id),
    FOREIGN KEY (parent_comment_id, post_id) REFERENCES post_comments(id, post_id)
);
CREATE INDEX IF NOT EXISTS ix_post_comments_page ON post_comments (post_id, created_at, id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_post_comments_parent ON post_comments (parent_comment_id, created_at, id) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS user_follows (
    follower_id INTEGER NOT NULL REFERENCES social_accounts(id),
    followed_id INTEGER NOT NULL REFERENCES social_accounts(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (follower_id, followed_id),
    CHECK (follower_id <> followed_id)
);
CREATE INDEX IF NOT EXISTS ix_user_follows_followed ON user_follows (followed_id, created_at DESC, follower_id);

CREATE TABLE IF NOT EXISTS saved_posts (
    user_id INTEGER NOT NULL REFERENCES social_accounts(id),
    post_id UUID NOT NULL REFERENCES cine_posts(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, post_id)
);
CREATE INDEX IF NOT EXISTS ix_saved_posts_page ON saved_posts (user_id, created_at DESC, post_id);

CREATE TABLE IF NOT EXISTS post_reposts (
    post_id UUID NOT NULL REFERENCES cine_posts(id),
    user_id INTEGER NOT NULL REFERENCES social_accounts(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (post_id, user_id)
);
CREATE INDEX IF NOT EXISTS ix_post_reposts_user ON post_reposts (user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS polls (
    id UUID PRIMARY KEY,
    post_id UUID NOT NULL UNIQUE REFERENCES cine_posts(id),
    question VARCHAR(120) NOT NULL,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS poll_options (
    id UUID PRIMARY KEY,
    poll_id UUID NOT NULL REFERENCES polls(id) ON DELETE CASCADE,
    option_text VARCHAR(60) NOT NULL,
    position SMALLINT NOT NULL,
    UNIQUE (poll_id, position),
    UNIQUE (poll_id, option_text),
    UNIQUE (poll_id, id)
);
CREATE TABLE IF NOT EXISTS poll_votes (
    poll_id UUID NOT NULL REFERENCES polls(id) ON DELETE CASCADE,
    option_id UUID NOT NULL,
    user_id INTEGER NOT NULL REFERENCES social_accounts(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (poll_id, option_id) REFERENCES poll_options(poll_id, id),
    PRIMARY KEY (poll_id, user_id)
);
CREATE INDEX IF NOT EXISTS ix_poll_votes_option ON poll_votes (option_id);

CREATE TABLE IF NOT EXISTS topics (
    id BIGSERIAL PRIMARY KEY,
    normalized_name VARCHAR(80) NOT NULL UNIQUE,
    display_name VARCHAR(80) NOT NULL
);
CREATE TABLE IF NOT EXISTS post_topics (
    post_id UUID NOT NULL REFERENCES cine_posts(id),
    topic_id BIGINT NOT NULL REFERENCES topics(id),
    PRIMARY KEY (post_id, topic_id)
);
CREATE INDEX IF NOT EXISTS ix_post_topics_topic ON post_topics (topic_id, post_id);

CREATE TABLE IF NOT EXISTS user_blocks (
    blocker_id INTEGER NOT NULL REFERENCES social_accounts(id),
    blocked_id INTEGER NOT NULL REFERENCES social_accounts(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (blocker_id, blocked_id),
    CHECK (blocker_id <> blocked_id)
);
CREATE TABLE IF NOT EXISTS user_mutes (
    muter_id INTEGER NOT NULL REFERENCES social_accounts(id),
    muted_id INTEGER NOT NULL REFERENCES social_accounts(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (muter_id, muted_id),
    CHECK (muter_id <> muted_id)
);
CREATE TABLE IF NOT EXISTS feed_preferences (
    user_id INTEGER PRIMARY KEY REFERENCES social_accounts(id),
    selected_genres TEXT[] NOT NULL DEFAULT '{}',
    selected_languages TEXT[] NOT NULL DEFAULT '{}',
    selected_post_types TEXT[] NOT NULL DEFAULT '{}',
    hide_spoilers BOOLEAN NOT NULL DEFAULT true,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS content_reports (
    id UUID PRIMARY KEY,
    post_id UUID NOT NULL REFERENCES cine_posts(id),
    reporter_id INTEGER NOT NULL REFERENCES social_accounts(id),
    reason VARCHAR(300) NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (post_id, reporter_id)
);

CREATE INDEX IF NOT EXISTS ix_cine_posts_search ON cine_posts USING gin (to_tsvector('simple', content)) WHERE deleted_at IS NULL;
