# ADR-52: `apiFetch` vs `authedFetch` split

**Status:** Accepted
**Date:** 2026-05-22 (Web Slice 2)
**Tags:** web, architecture

## Context
About 90% of frontend traffic is public reads (search, trending, channel views, suggestions) that don't need a session. The remaining 10% is authed (favorites, upload, profile edit, dev keys). Routing all calls through the same-origin Next Route Handler proxy adds an unnecessary hop to the dominant path; routing all calls direct to Spring breaks the cookie-backed auth model.

## Decision
Two distinct fetch wrappers in `lib/api/`:
- `apiFetch(path, opts)` — talks to Spring directly (CORS-allowed), no auth. Used for public reads from both browser and server.
- `authedFetch(path, opts)` — talks to Next.js Route Handlers (same-origin), cookie-backed. Used for any session-bound call, with built-in 401-refresh-retry.

## Rationale
- Avoids the same-origin hop for the dominant traffic class (public reads).
- Keeps authed calls XSS-safe by routing through the cookie-proxy chain.
- Two functions with disjoint trust models is easier to audit than one function with a "needs auth?" flag.

## Consequences
- Developers pick the right wrapper — naming is the affordance (`apiFetch` = anonymous, `authedFetch` = session). Code review enforces it.
- Adding an auth-optional endpoint (sees more if logged in) means choosing one path; today we pick `authedFetch` and accept the hop.
- The split is the architectural reason `/api/v1/channels/{username}` is callable from a server component via `apiFetch` without going through Next's own `/api/*`.
