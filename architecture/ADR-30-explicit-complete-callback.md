# ADR-30: Explicit `/complete` callback over S3 events

**Status:** Accepted
**Date:** 2026-05-21 (Slice 2)
**Tags:** backend, storage, media

## Context
After the browser's presigned PUT (ADR-29) succeeds, the backend must learn the upload is done so it can transition state and enqueue transcode. Two options: (a) subscribe to S3 ObjectCreated events via SNS/SQS, or (b) require the client to POST `/api/v1/media/upload/{id}/complete` after a successful PUT.

## Decision
Client posts the explicit `/complete` callback. The backend HEADs S3 to verify the object actually exists (defense against a lying client), then transitions the upload row and enqueues the transcode job.

## Rationale
- S3 events are eventually-consistent and not deduplicated; you build idempotency anyway.
- Client-driven completion is explicit, testable end-to-end without an SNS-bridge in the loop, and trivially retryable (POST is idempotent thanks to the state machine).
- We HEAD S3 on the callback, so the client can't claim a phantom upload — the worst they can do is fail to call us.
- S3 events make sense for drop-in third-party uploads where there's no client; here we own the client.

## Consequences
- If the client crashes between PUT and `/complete`, the upload row sits in `AWAITING_UPLOAD` forever. Mitigation: a sweeper job that times out stale rows (deferred; the orphan object in S3 has a separate lifecycle rule).
- One extra HTTP round trip from the client. Negligible vs. the upload itself.
- Local development needs no SNS/SQS plumbing — works against MinIO out of the box.
