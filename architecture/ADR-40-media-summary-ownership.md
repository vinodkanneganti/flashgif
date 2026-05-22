# ADR-40: `MediaSummary` lives in `media.api.dto` (cross-module DTO ownership)

**Status:** Accepted
**Date:** 2026-05-21 (Slice 5)
**Tags:** backend, modeling, contract

## Context
`MediaSummary` was born in Slice 1 inside `search.api.dto` because `search` was its first consumer. Slice 5 needed the same shape for channel sidebars (`topMedia: [MediaSummary]`). Two options: (a) duplicate the type in `channels.api.dto`, or (b) move it to `media.api.dto` and have both consumers import it.

## Decision
Move `MediaSummary` to `media.api.dto`. Add a `MediaSummary.from(Media)` static factory for the JPA-entity-to-DTO path. Update the four `search/` imports.

## Rationale
- DTO ownership should follow the data, not the first consumer. The shape is "summary of a media row" — that's a media concern.
- Two consumers and counting (search, channels; favorites and collections will likely follow) — duplication would mean drift.
- The factory keeps the projection logic in one place; previously search and channels would have written equivalent constructors independently.

## Consequences
- Set a precedent: when a second module wants an existing DTO, move it to the owning module rather than copying. This kept the `search` change to two import-line tweaks.
- `media` is now the canonical owner of any DTO whose data lives in the `media` table — future cross-module shapes should land there from the start.
- Reverse-direction dependencies are blocked: `search` and `channels` import from `media`, never the other way. The dependency graph stays acyclic.
- Module boundaries (ADR-01) are still respected — only DTOs cross, not entities or repositories.
