# ADR-37: Popularity recompute as 5-min batch + outbox

**Status:** Accepted
**Date:** 2026-05-21 (Slice 4)
**Tags:** backend, search, batch

## Context
`media.popularity` is a function of `favorite_count`, `view_count`, and age (`log(1 + favorite_count*3 + view_count) * exp(-age_days/7)`). Every favorite click changes the score; emitting an outbox event per click would flood the indexer for hot content and amplify writes for negligible ranking deltas.

## Decision
`PopularityRecomputeJob` runs `@Scheduled(fixedDelay = 5m, initialDelay = 1m)`. It selects media updated in the last 15 minutes, recomputes the score, and writes a `media.updated` outbox event only when the delta exceeds `MIN_CHANGE = 0.01f`. The existing outbox poller (ADR-17) carries it to ES.

## Rationale
- Batches naturally coalesce: a viral row favorited 1000× between runs gets one ES update, not 1000.
- 5-minute freshness is well within the trending grid's acceptable staleness; the cache TTL on `/trending` is 60s anyway (ADR-21).
- Reuses the outbox bridge — no new indexing path, no special handling on the consumer side.
- The skip threshold prevents float-drift recomputes from churning the index.

## Consequences
- Worst-case staleness on the popularity-driven ranking ≈ 5 min + 2s poller + 60s cache. Acceptable for trending.
- If the job stops running, popularity decays from "yesterday's truth" to "stale" — alert on job last-success timestamp.
- The 15-min lookback window must exceed the job interval with margin to tolerate a missed run; current 5m / 15m gives 3x.
- New scoring inputs (engagement, comment count) are added to the formula in one place — no per-event recompute scattered through services.
