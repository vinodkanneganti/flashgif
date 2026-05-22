# ADR-21: Per-cache TTL via `RedisCacheManager`

**Status:** Accepted
**Date:** 2026-05-20 (Slice 1)
**Tags:** backend, cache

## Context
Slice 1 introduced two read caches with materially different traffic shapes: `trending` (one or two hot keys, high QPS) and `suggestions` (broad prefix key space, lower per-key QPS). A single global TTL would either churn trending too slowly or expire suggestions before they earn their keep.

## Decision
Configure Spring's `RedisCacheManager` with named per-cache TTLs in `infra/cache/CacheConfig`:
- `trending` — 60s
- `suggestions` — 5m

Both use the same JSON value serializer (see ADR-20); only TTL diverges per cache name.

## Rationale
- `trending` is computed from a recently-changing popularity score; 60s keeps the grid fresh without flooring the cache hit rate.
- `suggestions` has a wide key space (every typed prefix); a 5-minute TTL lets warm prefixes stay resident long enough to actually amortize the ES round-trip.
- Per-cache TTLs are a one-line addition to `RedisCacheManager.builder()` — no reason to settle for one global value.

## Consequences
- Cache names are config keys; renaming a `@Cacheable("trending")` requires updating `CacheConfig` (caught by tests if we miss it — the cache silently uses default TTL).
- New caches must be added explicitly to keep their TTL intentional; falling back to the default is a code smell to be caught in review.
- Tuning is just a config tweak — no code change to either service.
