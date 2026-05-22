# ADR-66: ISR cached empty initial during bug window

**Status:** Accepted
**Date:** 2026-05-22 (Web Slice 1)
**Tags:** bug, web, rendering

## Context
**Symptom:** Home page (`/`) rendered an empty trending grid for ~60 seconds even after we'd fixed the underlying data path and confirmed `/api/v1/trending` returned 10 rows.

Root cause: `/` was `export const revalidate = 60` (ISR). The first SSR render happened while [ADR-67](ADR-67-bug-cache-serializer-erasure.md) (cache serializer erasure) was still broken — `getTrending()` threw, the catch returned `initial = []`, and Next.js cached that empty payload as the ISR snapshot. The 60s TTL then served `[]` to every visitor until expiry, regardless of subsequent fixes.

## Decision
Switch `/` to `export const dynamic = "force-dynamic"`. The page now SSRs fresh on every request; no ISR caching of bad initial data. Plan is to re-enable ISR (and tune the TTL) once the data path is provably stable under load.

## Rationale
- ISR is great for stable content. During active development of the data path, ISR is the wrong mode — it locks in failures and you can't see the fix.
- A shorter `revalidate` (e.g., 10s) would have reduced the blast radius but not fixed the principle: caching bad SSR is worse than no SSR cache.
- Could have wrapped `getTrending()` to throw on empty instead of returning `[]`, which would skip the ISR cache via Next.js's error path. Rejected as papering over the issue.

## Consequences
- The home page now hits the backend per request. Acceptable: the backend has its own Redis-backed trending cache ([ADR-21](ADR-21-per-cache-ttl.md), 60s TTL) so the actual DB/ES load is the same as ISR would have produced.
- Re-enabling ISR is a one-line change once we trust the data path. Worth doing — the SSR-per-request cost is real at scale.
- New rule: `dynamic = "force-dynamic"` is the default for any page whose data path is newer than ~1 sprint. Promote to ISR only after the failure modes are known.
