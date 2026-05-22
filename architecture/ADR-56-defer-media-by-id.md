# ADR-56: Defer GET `/api/v1/media/{id}`

**Status:** Deferred
**Date:** 2026-05-22 (Web Slice 4)
**Tags:** backend, web, api

## Context
Web favorites and collection-items endpoints return `media_id` lists. To render those as proper `<MediaCard>` tiles the web client needs to fetch the full media row by ID. The backend doesn't expose `GET /api/v1/media/{id}` today — historically there's been no caller (search returns full `MediaSummary`, channel feed returns full `MediaSummary`).

## Decision
Don't add the endpoint in Web Slice 4. Render `MediaTilePlaceholder` (ADR-54) for now. Add the endpoint as a small follow-up backend change, then swap the placeholder for `<MediaCard>` and add a `useMedia(id)` React Query hook.

## Rationale
- The endpoint is straightforward (5-10 LOC + a controller test) but ships separately from the web slice it unblocks.
- The placeholder UX is good enough to demo and to validate the favorites flow end-to-end without it.
- Defines a clean cut-line between Web Slice 4 (favorites mechanics) and the follow-up (visual polish).

## Consequences
- Favorites and collection pages look unfinished — acknowledged tradeoff.
- The follow-up touches three modules: a controller in `media`, a React Query hook in `lib/query/`, and a swap in `FavoritesList` + `CollectionDetail`.

## Trigger to revisit
First user feedback about the favorites page looking unfinished, OR before mobile clients start consuming favorites (mobile can't fall back to placeholders gracefully).
