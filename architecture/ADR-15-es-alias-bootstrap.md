# ADR-15: Index alias bootstrap (`media → media_v1`)

**Status:** Accepted
**Date:** 2026-05-20 (Slice 1)
**Tags:** backend, search, ops

## Context
Elasticsearch indexes are immutable in mapping for most field types. Schema evolution (new analyzer, changed field type) requires a new index. If clients query the index by name directly, every schema change forces a coordinated client cutover.

## Decision
`IndexInitializer` (an `ApplicationRunner`) creates `media_v1` from `resources/elasticsearch/media-mapping.json` and points the alias `media` at it on every boot. Idempotent — existing index/alias detected and left alone. All app code queries `media`, never `media_v1`.

## Rationale
- Aliases are ES's standard pattern for zero-downtime reindexing — build `media_v2`, atomic-swap the alias, drop `media_v1`.
- Bootstrapping on boot keeps the dev experience one-step (`docker compose up && bootRun` produces a working index); production runs the same code path against a real cluster.
- The mapping JSON is the source of truth; the `@Document` annotation on `MediaDocument` is informational (`createIndex = false`).

## Consequences
- `IndexInitializer` runs on every boot; must be cheap and idempotent (it is).
- Reindexing playbook: build `media_v2`, re-run outbox events (or scan Postgres), swap alias atomically, retire `media_v1`. Documented as a deferred runbook task.
- Direct queries against `media_v1` would silently bypass aliases — reviewers watch for it.
