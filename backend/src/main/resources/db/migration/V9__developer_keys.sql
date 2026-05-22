-- V9: Developer API keys.
-- Long-lived per-developer credentials for third-party integrations.
-- Hash-only persistence (raw key shown to owner exactly once at creation).

CREATE TABLE developer_keys (
    id            uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id      uuid         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name          varchar(100) NOT NULL,
    key_hash      bytea        NOT NULL UNIQUE,            -- SHA-256 of the raw token
    prefix        varchar(16)  NOT NULL,                   -- "fg_abcdefgh" for UI display
    status        varchar(16)  NOT NULL DEFAULT 'active'
                  CHECK (status IN ('active','revoked')),
    created_at    timestamptz  NOT NULL DEFAULT now(),
    last_used_at  timestamptz,
    revoked_at    timestamptz
);

CREATE INDEX idx_developer_keys_owner        ON developer_keys (owner_id);
CREATE INDEX idx_developer_keys_owner_active ON developer_keys (owner_id) WHERE status = 'active';
