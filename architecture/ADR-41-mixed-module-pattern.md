# ADR-41: Mixed-module pattern (server + client API in one file)

**Status:** Accepted
**Date:** 2026-05-22 (Web Slice 1)
**Tags:** web, architecture

## Context
Several API modules (`lib/api/channels.ts`, `lib/api/auth.ts`, `lib/api/media.ts`) export functions that are called from both server components (SSR, direct-to-Spring) and client components (browser → Next Route Handler proxy). Splitting them into `*.server.ts` + `*.client.ts` would double the file count and the type imports.

## Decision
Keep server + client callers in the same module. Server-only functions read cookies via `next/headers` and call Spring directly; client-only functions go through `authedFetch` / `apiFetch`. Each function declares its callsite in its name (`getCurrentUserFromCookie` vs `useMe`'s fetcher) and never crosses into the other half.

## Rationale
- DTOs and Zod schemas are shared between the two callsites — colocating them avoids re-exports and a fan-out of import statements.
- Next 14's `"use client"` boundary is enforced at the component level, not the module level — server-only utilities can live in a file imported by client components as long as the *function* isn't called from the client bundle.
- Tree-shaking removes server-only paths from the client bundle automatically.

## Consequences
- Function naming carries the boundary. Reviewers check that browser callsites never invoke a `*FromCookie` / `*Server` function.
- A misplaced server import (e.g., `next/headers`) would bleed into a client bundle — caught by the build, but worth scanning for in review.
- If the boundary becomes muddier (server + client paths sharing a state machine), split the module then — premature splitting earns nothing.
