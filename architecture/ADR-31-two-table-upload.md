# ADR-31: Two-table upload state machine (`media_uploads` + `media`)

**Status:** Accepted
**Date:** 2026-05-21 (Slice 2)
**Tags:** backend, data, media

## Context
An upload goes through several states (reserved, uploaded, transcoded, metadata-submitted, published) before becoming a searchable media item. A naive single-table design puts the searchable corpus and the in-flight pipeline state in the same rows — `search` has to filter half-baked uploads on every query, and abandoned uploads pollute the entity table forever.

## Decision
Two tables with distinct lifecycles:
- `media_uploads` — the pipeline state machine. States: `AWAITING_UPLOAD → UPLOADED → PROCESSING → READY → PUBLISHED`, plus terminal `FAILED`. One service (`UploadService`) owns transitions.
- `media` — the published, searchable entity. Created only when the user submits metadata against a `READY` upload. One service (`PublishService`) owns creation. This is what's indexed in ES.

## Rationale
- Lifecycle separation: search code never has to know about pipeline states.
- Abandoned uploads never become Media rows; `media` stays clean.
- Two services with one responsibility each is easier to reason about than one service with a giant state machine.

## Consequences
- Two tables, two services, one extra row per successful publish. Worth it for the clean `media` invariants.
- The transition `READY → PUBLISHED` is the moment a Media row is born; the outbox event fires here, not on upload completion (see ADR-35).
- Querying "all uploads in flight for this user" is a separate query against `media_uploads` — not joined into the user's public history.
- Failed uploads stay as `FAILED` rows for audit; a separate cleanup policy can age them out.
