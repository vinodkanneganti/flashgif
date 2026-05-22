# ADR-54: `MediaTilePlaceholder` for missing GET `/media/{id}`

**Status:** Accepted
**Date:** 2026-05-22 (Web Slice 4)
**Tags:** web, ux

## Context
Favorites and collection-items endpoints return `media_id` lists. The web client has no `GET /api/v1/media/{id}` endpoint to rehydrate full media rows (ADR-56 defers that backend addition). Without it, the favorites and collection-detail pages have nothing to render beyond an ID.

## Decision
Render `<MediaTilePlaceholder mediaId timestamp />` — a same-shape tile that shows the media ID and the favorited-at timestamp inside a grid cell sized like a real `MediaCard`. Layout stays correct; visuals are intentionally minimal.

## Rationale
- The grid skeleton is right — users can see the count, the order, and the timing of their favorites.
- Swapping placeholder → `<MediaCard>` later is a one-component change in `FavoritesList` and `CollectionDetail`.
- Avoids blocking the Web Slice 4 ship on a backend endpoint that's small but isn't done.

## Consequences
- Favorites page is functional but ugly. Documented in the slice's "known limitations" and tracked by ADR-56.
- The placeholder component lives in `components/favorites/` — when ADR-56 is closed, delete the import and the file.
- Reviewers should not extend the placeholder with more features (would make the swap harder); it's intentionally throwaway.
