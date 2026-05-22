# ADR-27: Pessimistic locking for counter mutations

**Status:** Accepted
**Date:** 2026-05-21 (Slice 4)
**Tags:** backend, data, concurrency

## Context
`media.favorite_count` is bumped on every favorite/unfavorite. Viral content concentrates writes on a tiny number of rows. The candidate concurrency strategies are optimistic (`@Version` + retry) and pessimistic (`SELECT … FOR UPDATE`).

## Decision
Use `@Lock(PESSIMISTIC_WRITE)` on `MediaRepository.findByIdForUpdate(...)` before incrementing the counter. Favorite/unfavorite paths fetch through this method inside the transaction.

## Rationale
- Optimistic locking on a hot row degenerates into retry storms — every retry re-reads, re-conflicts, re-aborts. Throughput collapses under contention.
- Pessimistic serializes the writes — backpressure shows up as latency, which is observable and survivable, not as lost updates or 5xx storms.
- The hot-row case is the one this mechanism exists for.

## Consequences
- Slight throughput cap on viral content's counter row. Acceptable for v1; favorite throughput is bounded by the user, not the system.
- If the cap bites: (a) move the counter to Redis with periodic flush, or (b) batch-fold per-second favorite deltas. Both are local changes — the rest of the model doesn't see them.
- Long-running transactions holding the lock will block other favoriters — keep the increment transaction tight (read, bump, commit; no external calls).
