CREATE TABLE cinechat.message_attachments (
    message_id UUID PRIMARY KEY REFERENCES cinechat.messages(id) ON DELETE CASCADE,
    media_id UUID NOT NULL UNIQUE REFERENCES cinefeed.post_media(id),
    owner_id INTEGER NOT NULL REFERENCES cinefeed.social_accounts(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE cinechat.conversation_avatars (
    conversation_id UUID PRIMARY KEY REFERENCES cinechat.conversations(id) ON DELETE CASCADE,
    media_id UUID NOT NULL UNIQUE REFERENCES cinefeed.post_media(id),
    owner_id INTEGER NOT NULL REFERENCES cinefeed.social_accounts(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
