# ADR-53: Optimistic favorite mutations (snapshot + rollback)

**Status:** Accepted
**Date:** 2026-05-22 (Web Slice 4)
**Tags:** web, ux

## Context
The heart button on a MediaCard should feel instant. A backend round-trip is ~100 ms warm — perceptible as latency. Worse, on infinite-scrolling grids the user may favorite several items before the first response returns, and a per-click spinner kills the rhythm.

## Decision
React Query `useMutation` with optimistic update:
1. `onMutate`: cancel in-flight queries on the affected key, snapshot the current cache, write the optimistic new state.
2. `onError`: restore the snapshot.
3. `onSettled`: invalidate the key to re-fetch canonical state.

Applied to favorite / unfavorite and to collection-item add / remove.

## Rationale
- Favoriting is idempotent — worst case on failure is one click "didn't take" and the user retries. Cheap rollback semantics.
- The snapshot + rollback dance is the React Query canonical pattern; no custom state machine to maintain.
- `onSettled` invalidation guarantees we converge on truth even if the optimistic state was slightly off (e.g., counter delta).

## Consequences
- Only operations that are idempotent and have a cheap rollback get this treatment. Operations with side effects (publishing media, deleting an account) stay non-optimistic.
- The pattern is documented in `favoritesHooks.ts` and gets reused for `collectionsHooks.ts`; new optimistic mutations should follow the same shape.
- If we ever cache counts derived from the favorite (favorite_count on the media card), the optimistic update has to bump them too — easy to miss.
