# ADR-51: SSR pre-fill for `useMe` (no flash)

**Status:** Accepted
**Date:** 2026-05-22 (Web Slice 2)
**Tags:** web, ssr, auth

## Context
On first page load for an authenticated user, the header would briefly render "Login / Sign up" while `useMe` fetched `/api/users/me`, then snap to "Hi, @username". This "auth flash" is jarring and a giveaway that the page wasn't rendered with context.

## Decision
Root `layout.tsx` is a server component that reads the access cookie via `next/headers`, calls Spring `/users/me` server-side, and seeds React Query with the result. The header receives `user` as a prop and is correct on first render. `useMe()` finds the cache already populated and skips its initial fetch.

## Rationale
- Server-rendered pages should have server-rendered state — anything else is a perceptible UX regression vs. classic SSR.
- One server-to-server call per page render is cheap (~10 ms warm); the UX win (no flash) is significant.
- React Query's `setQueryData` seeding integrates cleanly — no special-case "initial user" plumbing in client code.

## Consequences
- `getCurrentUserFromCookie()` bypasses the Route Handler proxy and calls Spring directly. One more place that knows the Spring base URL.
- If the cookie is stale (Spring returns 401), the server treats the user as anonymous — the client `useMe` hook will retry and refresh if it can.
- Adds a small SSR cost on every page; acceptable because every page renders the header anyway.
