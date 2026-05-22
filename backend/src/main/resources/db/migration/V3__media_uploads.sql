-- V3: Media upload pipeline state.
-- Separate from the `media` table — uploads can fail, be abandoned, or sit
-- waiting for metadata; only successful + metadata-submitted uploads ever
-- create a row in `media`.

CREATE TABLE media_uploads (
    id                      uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    uploader_id             uuid,                                              -- nullable until Users slice
    original_filename       varchar(255)  NOT NULL,
    original_content_type   varchar(64)   NOT NULL,
    original_size           bigint        NOT NULL,
    s3_key                  varchar(512)  NOT NULL,                            -- e.g. uploads/{id}/{filename}
    status                  varchar(24)   NOT NULL DEFAULT 'AWAITING_UPLOAD'
                                          CHECK (status IN (
                                              'AWAITING_UPLOAD',
                                              'UPLOADED',
                                              'PROCESSING',
                                              'READY',
                                              'FAILED',
                                              'PUBLISHED'
                                          )),
    error_message           text,
    width                   int,
    height                  int,
    duration_ms             int,
    rendition_urls          jsonb,                                             -- {mp4, webp, gif, poster}
    media_id                uuid          REFERENCES media(id) ON DELETE SET NULL,
                                                                                -- set when status flips to PUBLISHED
    created_at              timestamptz   NOT NULL DEFAULT now(),
    updated_at              timestamptz   NOT NULL DEFAULT now(),
    completed_at            timestamptz
);

CREATE INDEX idx_media_uploads_status_created ON media_uploads (status, created_at);
CREATE INDEX idx_media_uploads_uploader       ON media_uploads (uploader_id) WHERE uploader_id IS NOT NULL;
