# ADR-70: `UploadService` violated `s3_key NOT NULL`

**Status:** Accepted
**Date:** 2026-05-23 (Backend Slice 2, latent — surfaced in Web 3)
**Tags:** bug, data

## Context
**Symptom:** `POST /api/v1/media/upload` returned `500`. Stacktrace: `DataIntegrityViolationException: null value in column "s3_key" of relation "media_uploads" violates not-null constraint`.

Root cause (latent since Slice 2): `UploadService.create()` did a `uploadRepository.saveAndFlush(upload)` to obtain the generated UUID, *then* computed `s3_key = "uploads/{id}/{name}"`, *then* `save()` again. The first flush hit Postgres before `s3_key` was set, and the column was correctly `NOT NULL` — boom.

Why it never surfaced earlier: Slice 2's smoke test was curl + Swagger, exercising response shapes rather than the full happy path. No actual `/upload` call hit Postgres. The web Upload modal was the first real end-to-end consumer.

## Decision
Generate the UUID up front in the service (`UUID.randomUUID()`), set both `id` and `s3_key` on the entity, then `save()` once. Spring Data does a SELECT-then-INSERT (merge path because id is non-null), which is functionally correct.

A future micro-optimisation: implement `Persistable<UUID>` on `MediaUpload` to skip the SELECT. Not worth it today.

## Rationale
- "Generate id in app code" is the cleanest fix — no two-phase commit, one INSERT, schema invariants preserved.
- Could have made `s3_key` nullable. Wrong direction — it's a `NOT NULL` invariant for a good reason (no upload row should exist without a target key).
- Could have used `@PostPersist` to compute the key after insert. Doesn't help — the constraint fires during the insert itself.

## Consequences
- General rule: if you need a generated id to compute a non-nullable sibling column, generate the id in app code, not in the DB. Avoids the two-phase write.
- This is one of four bugs ([ADR-70](ADR-70-bug-s3-key-not-null.md), [ADR-71](ADR-71-bug-dispatch-race.md), [ADR-72](ADR-72-bug-libwebp-missing.md), [ADR-73](ADR-73-bug-minio-bucket-private.md)) that lay latent in Backend Slice 2 and all surfaced within ~30 minutes once the web upload modal exercised the full pipeline. The strongest argument so far for the Testcontainers integration debt ([ADR-57](ADR-57-defer-testcontainers.md)).
