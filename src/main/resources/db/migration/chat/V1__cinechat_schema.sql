CREATE SCHEMA IF NOT EXISTS cinechat;

CREATE TABLE cinechat.conversations (
    id UUID PRIMARY KEY,
    kind VARCHAR(12) NOT NULL CHECK (kind IN ('DIRECT', 'GROUP')),
    title VARCHAR(100),
    creator_id INTEGER NOT NULL REFERENCES cinefeed.social_accounts(id),
    direct_key VARCHAR(32) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_message_at TIMESTAMPTZ,
    CHECK ((kind = 'DIRECT' AND direct_key IS NOT NULL AND title IS NULL)
        OR (kind = 'GROUP' AND direct_key IS NULL AND title IS NOT NULL))
);

CREATE TABLE cinechat.conversation_members (
    conversation_id UUID NOT NULL REFERENCES cinechat.conversations(id) ON DELETE CASCADE,
    user_id INTEGER NOT NULL REFERENCES cinefeed.social_accounts(id),
    role VARCHAR(12) NOT NULL DEFAULT 'MEMBER' CHECK (role IN ('ADMIN', 'MEMBER')),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    left_at TIMESTAMPTZ,
    last_read_at TIMESTAMPTZ,
    PRIMARY KEY (conversation_id, user_id)
);
CREATE INDEX ix_chat_members_user ON cinechat.conversation_members(user_id, joined_at DESC)
    WHERE left_at IS NULL;

CREATE TABLE cinechat.messages (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES cinechat.conversations(id) ON DELETE CASCADE,
    sender_id INTEGER NOT NULL REFERENCES cinefeed.social_accounts(id),
    client_id UUID NOT NULL,
    kind VARCHAR(16) NOT NULL DEFAULT 'TEXT'
        CHECK (kind IN ('TEXT', 'POLL', 'MOVIE', 'CINE_TAKE')),
    body VARCHAR(4000) NOT NULL DEFAULT '',
    reply_to_id UUID REFERENCES cinechat.messages(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    UNIQUE (conversation_id, sender_id, client_id),
    CHECK (char_length(body) <= 4000)
);
CREATE INDEX ix_chat_messages_page ON cinechat.messages(conversation_id, created_at DESC, id DESC);
CREATE INDEX ix_chat_messages_sender ON cinechat.messages(sender_id, created_at DESC);

CREATE TABLE cinechat.message_receipts (
    message_id UUID NOT NULL REFERENCES cinechat.messages(id) ON DELETE CASCADE,
    user_id INTEGER NOT NULL REFERENCES cinefeed.social_accounts(id),
    delivered_at TIMESTAMPTZ,
    read_at TIMESTAMPTZ,
    PRIMARY KEY (message_id, user_id)
);
CREATE INDEX ix_chat_receipts_user_read ON cinechat.message_receipts(user_id, read_at);

CREATE TABLE cinechat.message_reactions (
    message_id UUID NOT NULL REFERENCES cinechat.messages(id) ON DELETE CASCADE,
    user_id INTEGER NOT NULL REFERENCES cinefeed.social_accounts(id),
    emoji VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (message_id, user_id, emoji)
);

CREATE TABLE cinechat.chat_polls (
    message_id UUID PRIMARY KEY REFERENCES cinechat.messages(id) ON DELETE CASCADE,
    question VARCHAR(240) NOT NULL,
    multiple_choice BOOLEAN NOT NULL DEFAULT false,
    closes_at TIMESTAMPTZ
);
CREATE TABLE cinechat.chat_poll_options (
    id UUID PRIMARY KEY,
    message_id UUID NOT NULL REFERENCES cinechat.chat_polls(message_id) ON DELETE CASCADE,
    label VARCHAR(120) NOT NULL,
    position SMALLINT NOT NULL,
    UNIQUE (message_id, position),
    UNIQUE (message_id, label)
);
CREATE TABLE cinechat.chat_poll_votes (
    option_id UUID NOT NULL REFERENCES cinechat.chat_poll_options(id) ON DELETE CASCADE,
    user_id INTEGER NOT NULL REFERENCES cinefeed.social_accounts(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (option_id, user_id)
);
CREATE INDEX ix_chat_poll_votes_user ON cinechat.chat_poll_votes(user_id, created_at DESC);
