# ADR-29: Direct browser to S3 presigned PUT upload

**Status:** Accepted
**Date:** 2026-05-21 (Slice 2)
**Tags:** backend, storage, media

## Context
Uploads are bounded at 100 MB. Two shapes for getting the bytes to S3: (a) browser PUTs to the backend, backend streams to S3; or (b) backend signs a short-lived presigned PUT URL, browser uploads directly to S3.

## Decision
Backend issues a presigned PUT URL (15-min expiry, scoped to one S3 key and one content type). Browser PUTs directly to S3. The backend never sees the bytes.

## Rationale
- A 50 MB upload via the backend ties up an API connection and outbound bandwidth for the whole upload duration — that's a pod we can't use for real requests.
- Presigned URLs are S3's purpose-built primitive for this; AWS SDK v2 generates them in one call.
- The two extra round trips (reserve + complete) are tiny compared to a multi-MB body.

## Consequences
- The backend stays small and fast — no upload-buffer tuning, no chunked-transfer edge cases, no proxy timeouts on slow uploads.
- The client is trusted to know the file size + type at reserve time (we sign for those exact values). Misreporting → S3 rejects the PUT.
- CORS must be configured on the bucket so the browser can PUT cross-origin. Done in `BucketBootstrapper` for dev; IaC in prod.
- Pairs with ADR-30 (explicit `/complete` callback) and ADR-31 (two-table upload state machine).
