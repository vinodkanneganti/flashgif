-- V2: Media + tags + transactional outbox.
-- Adds the schema the Search slice needs end-to-end.

CREATE TABLE media (
    id              uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    uploader_id     uuid,                                            -- nullable until Users slice lands
    title           varchar(200) NOT NULL,
    description     text,
    type            varchar(16)  NOT NULL CHECK (type IN ('gif','sticker')),
    content_rating  varchar(8)   NOT NULL DEFAULT 'g'
                                  CHECK (content_rating IN ('g','pg','pg13','r')),
    status          varchar(16)  NOT NULL DEFAULT 'published'
                                  CHECK (status IN ('processing','published','rejected')),
    view_count      bigint       NOT NULL DEFAULT 0,
    favorite_count  bigint       NOT NULL DEFAULT 0,
    popularity      real         NOT NULL DEFAULT 0,
    rendition_urls  jsonb,                                           -- {"gif":"...","mp4":"...","webp":"...","poster":"..."}
    width           int,
    height          int,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX idx_media_status_created_at ON media (status, created_at DESC);
CREATE INDEX idx_media_popularity        ON media (popularity DESC) WHERE status = 'published';

CREATE TABLE media_tags (
    media_id  uuid        NOT NULL REFERENCES media(id) ON DELETE CASCADE,
    tag       varchar(64) NOT NULL,
    PRIMARY KEY (media_id, tag)
);
CREATE INDEX idx_media_tags_tag ON media_tags (tag);

-- Transactional outbox: written in the same tx as the domain change,
-- drained by search/sync/OutboxPoller, which marks published_at on success.
CREATE TABLE outbox_events (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  varchar(64) NOT NULL,
    aggregate_id    uuid        NOT NULL,
    event_type      varchar(64) NOT NULL,
    payload         jsonb       NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    published_at    timestamptz
);
CREATE INDEX idx_outbox_unpublished
    ON outbox_events (created_at)
    WHERE published_at IS NULL;
