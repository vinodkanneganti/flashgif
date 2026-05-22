# ADR-28: In-memory token bucket (defer Bucket4j-Redis)

**Status:** Deferred
**Date:** 2026-05-21 (Slice 6)
**Tags:** backend, rate-limit

## Context
Developer API needs per-key rate limiting (60 req/min default). A distributed deployment would require shared counter state — the canonical answer is Bucket4j on Redis. But Slice 0 hit unresolvable dep-resolution issues with Bucket4j's published Maven coords, and v1 only ever runs on a single instance.

## Decision
Hand-rolled token bucket in `ConcurrentHashMap<UUID, Bucket>`, refilled lazily on access. Bucket4j-Redis is the documented upgrade path; the limiter sits behind a `RateLimiter` interface so the swap is one-bean.

## Rationale
- Single instance today → distributed state would be premature complexity for zero benefit.
- Zero Redis round-trip per request — a real latency win on the rate-limit hot path.
- Avoids the Bucket4j Maven-coord rabbit hole that ate Slice 0 time.
- Interface boundary keeps the upgrade option open at near-zero cost.

## Consequences
- Rate-limit state is lost on pod restart — every bucket resets to full. Acceptable in dev; in prod it would mean a burst window on first request after deploy.
- Cannot horizontally scale the developer API tier without giving up rate-limit correctness (each pod has its own bucket map; an attacker could fan out across pods).
- Memory bound = `O(active API keys)`; with a TTL eviction sweep this stays in megabytes for tens of thousands of keys.

## Trigger to revisit
Second developer API pod is provisioned, OR rate-limit accuracy becomes a customer-facing SLA, OR active key count grows past what fits comfortably in heap.
