# ADR-13: Elasticsearch as read-only secondary

**Status:** Accepted
**Date:** 2026-05-20 (Slice 1)
**Tags:** backend, data, search

## Context
Postgres FTS handles match + stem but not (a) typo-tolerant fuzzy search, (b) `search_as_you_type` autocomplete with edge n-grams, or (c) ranking that blends text relevance with a recomputed popularity score via `function_score`. The search hot path needs all three.

## Decision
A separate Elasticsearch cluster holds a denormalised `media_v1` index, kept read-only and synced from Postgres via the transactional outbox pattern (see ADR-16). Writes always go to Postgres; ES is rebuilt from Postgres if it diverges.

## Rationale
- Two storage engines for two access patterns — Postgres for authoritative writes, ES for read-mostly search.
- ES's analyzers, fuzziness, and `function_score` are first-class; reproducing them in Postgres FTS would be a multi-month project at lower quality.
- Read-only ES means we never face dual-write inconsistency from clients; the outbox is the only writer.

## Consequences
- Eventual consistency on the search index (p95 lag ≤5s with the 2-second poller).
- Operational cost: a second stateful service to monitor, back up, and reindex.
- If ES is down, search degrades; the rest of the API stays up.
- ES schema changes are zero-downtime via alias swap (see ADR-15).
