-- V4: Users + refresh-token store. Wires uploader_id back-reference onto media.

CREATE EXTENSION IF NOT EXISTS citext;

CREATE TABLE users (
    id              uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    email           citext       UNIQUE NOT NULL,
    password_hash   text         NOT NULL,
    display_name    varchar(50)  NOT NULL,
    status          varchar(16)  NOT NULL DEFAULT 'active'
                                  CHECK (status IN ('active','disabled')),
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now()
);

-- Rotating opaque refresh tokens. token_hash = SHA-256 of the raw token shown
-- to the client; raw token is never stored.
CREATE TABLE refresh_tokens (
    id              uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash      bytea        NOT NULL UNIQUE,
    expires_at      timestamptz  NOT NULL,
    revoked_at      timestamptz,
    replaced_by     uuid         REFERENCES refresh_tokens(id),
    user_agent      varchar(255),
    ip              inet,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    last_used_at    timestamptz
);
CREATE INDEX idx_refresh_user_active ON refresh_tokens (user_id) WHERE revoked_at IS NULL;

-- media.uploader_id was already added in V2 (nullable, no FK because the
-- users table didn't exist yet). Now that users exists, attach the FK and
-- add the supporting partial index.
ALTER TABLE media
    ADD CONSTRAINT fk_media_uploader
    FOREIGN KEY (uploader_id) REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX idx_media_uploader ON media (uploader_id) WHERE uploader_id IS NOT NULL;
