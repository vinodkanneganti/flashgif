# ADR-19: Redis for cache + rate-limit + dev usage counters

**Status:** Accepted
**Date:** 2026-05-20 (Slice 1)
**Tags:** backend, cache, infra

## Context
Three distinct needs surfaced across the slices: (1) cache trending/suggestions to keep ES off the hot path, (2) per-API-key rate limiting for the developer chain, (3) cheap atomic counters for developer usage analytics. Each has solo solutions (Caffeine for in-process cache, Bucket4j-in-memory for rate limiting, a counter table for analytics) but a shared Redis covers all three.

## Decision
One Redis instance handles all three workloads. `RedisCacheManager` for Spring cache abstraction (trending, suggestions), `INCR` with TTL for developer usage counters (`dev:usage:{keyId}:{yyyyMMdd}`). Rate limiting starts in-process (ADR-28) and can migrate to Bucket4j-Redis when we go multi-instance.

## Rationale
- One stateful service to operate instead of three.
- Redis is the right tool for each individual job; the only question was whether to share an instance or run separate ones.
- Counters in Redis avoid hot-row Postgres contention on the dev-API request path.
- Cache and counters use disjoint key namespaces; no conflict risk.

## Consequences
- Redis is a load-bearing dependency for cached endpoints. Cache layer is "best-effort" — if Redis is down, calls fall through to ES/DB; if `INCR` fails, the request still succeeds (we log and move on).
- A `FLUSHDB` clears all three concerns at once — runbook documents when that's appropriate (cache schema change, ADR-67).
- Production Redis is a managed offering (ElastiCache); same client config.
