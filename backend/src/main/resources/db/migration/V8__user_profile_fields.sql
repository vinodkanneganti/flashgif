-- V8: Public-channel profile fields on users.
-- Every user is potentially a creator (PRD has no separate creator-application
-- flow), so these fields live on the users row 1:1 rather than in a side table.
-- All nullable / safe defaults — no backfill required.

ALTER TABLE users
    ADD COLUMN bio          text,
    ADD COLUMN website_url  varchar(255),
    ADD COLUMN avatar_url   varchar(500),
    ADD COLUMN banner_url   varchar(500),
    ADD COLUMN social_links jsonb,                                  -- {twitter, instagram, tiktok, youtube, github}
    ADD COLUMN is_verified  boolean NOT NULL DEFAULT false;
