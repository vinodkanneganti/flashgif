# ADR-65: TS DTOs typed snake_case from `@Schema` vs camelCase wire

**Status:** Accepted
**Date:** 2026-05-22 (Web Slice 1)
**Tags:** bug, contract

## Context
**Symptom:** Playwright e2e showed `MediaCard` rendering `undefined` for `title`, `width`, `height`, etc. on the trending grid. Network tab confirmed the API returned `{"title": "...", "renditionUrls": {...}}` — but the TS types said `title_text`, `rendition_urls`.

Root cause: TS types were generated from `docs/openapi.yaml`, where the backend's `@Schema(name = "title_text")` annotations on DTO fields appear as snake_case. But those `@Schema(name = ...)` overrides only affect the OpenAPI document — Jackson's actual serialization uses the field name (camelCase) unless `spring.jackson.property-naming-strategy` is set. So the documented contract said snake_case and the wire format was camelCase. The TS types matched the doc; the runtime didn't.

## Decision
Short-term fix: switch TS DTO shapes and `MediaCard` field refs to camelCase to match what Jackson actually emits. Unblocked the slice.

Follow-up (landed in a later session): set `spring.jackson.property-naming-strategy=SNAKE_CASE` server-side so JSON matches the documented schema names. The web side then reverted its types to snake_case. See [ADR-34](ADR-34-snake-case-json.md) for the final canonical contract.

## Rationale
- Two truths in a contract is one too many. Either the OpenAPI doc is wrong or the wire is wrong — pick one and align.
- Snake_case on the wire is the documented PRD convention and the easier story for mobile clients (Kotlin/Swift JSON libs both default to snake_case-friendly mappings). So the long-term fix moves Jackson, not the doc.
- Cache `ObjectMapper` was deliberately not changed — internal-only, see [ADR-20](ADR-20-cache-objectmapper.md).

## Consequences
- Anyone regenerating TS types after the Jackson change has to also flip the field refs in components. Tracked in the Web Slice 1 retrospective.
- New rule: never trust `@Schema(name = ...)` alone to dictate wire format — verify with an actual `curl` of the endpoint before typing the client.
- Reinforces [ADR-46](ADR-46-openapi-typescript-types-only.md)'s "types only, no runtime client" choice — a runtime client would have failed harder and earlier, but the type-only approach was salvageable with a one-line rename pass.
