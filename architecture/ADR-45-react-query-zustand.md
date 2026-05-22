# ADR-45: React Query for server state + Zustand for UI state

**Status:** Accepted
**Date:** 2026-05-22 (Web Slice 1)
**Tags:** web, state

## Context
The app has two distinct categories of state: server-derived (search results, `/me`, favorites, dev keys — cacheable, refetchable, mutation-driven) and ephemeral UI (modal open/closed, theme, transient form flags). Mixing them under a single Redux-style store is the standard React footgun — server state ends up duplicated, stale, and manually invalidated.

## Decision
TanStack Query v5 owns everything that comes from the backend. Zustand (~1 KB) handles the truly local UI state. No Redux, no Context-as-state.

## Rationale
- React Query was built specifically to solve the server-state problem — caching, refetch-on-focus, optimistic updates, infinite query, mutation lifecycle.
- Zustand stays out of React Query's way: it's for things React Query shouldn't know about (is the upload modal open?).
- Two libraries with disjoint responsibilities are easier to reason about than one library doing both badly.

## Consequences
- Devs need to know which lives where. Rule of thumb: "if it can be re-fetched, it's React Query."
- React Query's cache is the source of truth for server data — mutations invalidate via the key factory (`lib/query/keys.ts`), never by hand-patching another store.
- Adding a new server resource means a new hook in `lib/query/*Hooks.ts`, not a new store slice.
