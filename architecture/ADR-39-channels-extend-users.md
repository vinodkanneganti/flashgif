# ADR-39: Channels extend `users` (no separate creator entity)

**Status:** Accepted
**Date:** 2026-05-21 (Slice 5)
**Tags:** backend, data, modeling

## Context
The PRD references a "CreatorEntity" for channel profiles (bio, avatar, banner, social links, verified badge). Two shapes for this: (a) a separate `creators` table with a FK to `users`, or (b) flat columns added directly to `users`.

## Decision
Extend `users` with channel-profile columns: `bio`, `website_url`, `avatar_url`, `banner_url`, `social_links` (JSONB), `is_verified`. No separate creator table; no separate creator service.

## Rationale
- Every user is potentially a creator; the relationship is strictly 1:1 with the same lifecycle. A separate table would force a join on every profile read for zero modeling benefit.
- All new columns are nullable / safe defaults — additive migration with no backfill.
- `username`, added in Slice 4 for collections URLs, already pays the cost of being a stable public identifier; channels reuse it.

## Consequences
- `users` table grows wider — fine, the columns are all small or nullable JSONB.
- `ChannelsService` is a thin composition over `UserRepository` + `MediaRepository`. No entities of its own. The whole channels module is API + service, no domain layer.
- If we ever need creator-scoped fields that aren't 1:1 with users (multiple channels per user, organization accounts), we extract then — schema migration is straightforward.
- The `is_verified` column has no PATCH endpoint by design — flipping it is a future admin concern, not a self-service action.
