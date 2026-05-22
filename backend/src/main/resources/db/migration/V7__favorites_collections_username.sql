-- V7: Favorites + Collections + user-handle (username).
--
-- Adds the schema for Slice 4 (favorites + curated folders) plus a new
-- public-handle column on users that Slice 4 (public-collections URL) and
-- Slice 5 (channels URL) both need.

-- ---------------------------------------------------------------------------
-- 1. users.username — public, URL-safe handle
-- ---------------------------------------------------------------------------
ALTER TABLE users ADD COLUMN username varchar(30);

-- Backfill: derive from email local part, sanitise to [a-zA-Z0-9_],
-- dedupe colliding handles by appending a suffix, pad short ones.
WITH ranked AS (
    SELECT id,
           created_at,
           REGEXP_REPLACE(
               SUBSTRING(email FROM 1 FOR POSITION('@' IN email) - 1),
               '[^a-zA-Z0-9_]', '_', 'g'
           ) AS base_username
    FROM users
), uniqued AS (
    SELECT id,
           base_username,
           ROW_NUMBER() OVER (PARTITION BY base_username ORDER BY created_at) AS dupe_rn
    FROM ranked
)
UPDATE users u
SET username = CASE
    WHEN uq.dupe_rn = 1 THEN uq.base_username
    ELSE uq.base_username || (uq.dupe_rn - 1)::text
END
FROM uniqued uq
WHERE u.id = uq.id;

-- Pad anything below 3 chars to meet the format check.
UPDATE users
SET username = username || REPEAT('_', GREATEST(3 - LENGTH(username), 0))
WHERE LENGTH(username) < 3;

ALTER TABLE users ALTER COLUMN username SET NOT NULL;
ALTER TABLE users ADD CONSTRAINT users_username_unique UNIQUE (username);
ALTER TABLE users ADD CONSTRAINT users_username_format
    CHECK (username ~ '^[a-zA-Z0-9_]{3,30}$');

-- ---------------------------------------------------------------------------
-- 2. favorites — flat per-user list
-- ---------------------------------------------------------------------------
CREATE TABLE favorites (
    user_id    uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    media_id   uuid        NOT NULL REFERENCES media(id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, media_id)
);
CREATE INDEX idx_favorites_user_created  ON favorites (user_id, created_at DESC);
CREATE INDEX idx_favorites_media         ON favorites (media_id);

-- ---------------------------------------------------------------------------
-- 3. media_collections — named, owned folders
--    (table name avoids java.util.Collection clash in JPA)
-- ---------------------------------------------------------------------------
CREATE TABLE media_collections (
    id          uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id    uuid         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        varchar(100) NOT NULL,
    description text,
    is_public   boolean      NOT NULL DEFAULT false,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_collections_owner        ON media_collections (owner_id);
CREATE INDEX idx_collections_public_owner ON media_collections (owner_id) WHERE is_public;

-- ---------------------------------------------------------------------------
-- 4. collection_items — many-to-many media inside a collection
-- ---------------------------------------------------------------------------
CREATE TABLE collection_items (
    collection_id uuid        NOT NULL REFERENCES media_collections(id) ON DELETE CASCADE,
    media_id      uuid        NOT NULL REFERENCES media(id)              ON DELETE CASCADE,
    added_at      timestamptz NOT NULL DEFAULT now(),
    position      int,                                                   -- reserved for reorder API
    PRIMARY KEY (collection_id, media_id)
);
CREATE INDEX idx_collection_items_collection_added
    ON collection_items (collection_id, added_at DESC);
