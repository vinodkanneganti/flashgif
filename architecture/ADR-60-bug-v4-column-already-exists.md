# ADR-60: V4 migration tried to add an already-existing column

**Status:** Accepted
**Date:** 2026-05-21 (Slice 3)
**Tags:** bug, data, migration

## Context
**Symptom:** App refused to boot. Flyway aborted V4 with `column "uploader_id" of relation "media" already exists`.

V2 (Search slice) had quietly added `uploader_id` to `media` as a nullable column with no FK — placeholder for "the indexer needs a username eventually". V4 (Users slice), written weeks later, treated `uploader_id` as a brand-new column and did `ADD COLUMN uploader_id` + FK + index in one shot. The author of V4 didn't grep for the column name across earlier migrations.

## Decision
V4 now adds only the missing pieces — the FK constraint (`fk_media_uploader`) and the partial index (`idx_media_uploader`). The column declaration stays in V2 where it was first introduced. No new migration was needed because Postgres rolled back the failed V4 transaction including Flyway's own `flyway_schema_history` insert — so the user could simply restart and apply the fixed V4.

## Rationale
- A schema column should be declared exactly once. Splitting "column + constraint" across migrations is fine as long as later migrations only add what's actually new.
- Renumbering or rewriting V2 to fold in the FK was the alternative — rejected because V2 was already applied in dev environments and rewriting an applied migration breaks Flyway's contract.
- Adding `IF NOT EXISTS` to V4's `ADD COLUMN` would have silently passed but masked the design slip; better to keep migrations strict and fix the duplication.

## Consequences
- Reinforces "every migration is reviewed against the union of all prior migrations" — `grep -r uploader_id db/migration/` should be a step in writing a new V*.
- Lucky recovery this time (Postgres-transactional DDL) — MySQL or any DB without transactional DDL would have left a `Failed` row in `flyway_schema_history` that needed manual `flyway repair`. Worth remembering if we ever support a second backend.
- Did not produce a new ADR for the schema rule — the lesson is procedural, captured in [ADR-11](ADR-11-flyway-owns-schema.md)'s "Flyway-owned schema" stance and reinforced here.
