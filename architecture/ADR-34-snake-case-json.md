# ADR-34: snake_case JSON across the public API

**Status:** Accepted (post-bug; see ADR-65)
**Date:** 2026-05-22 (Web 1, codified post-Bug 65)
**Tags:** backend, contract, api

## Context
Slice 1 entities used Jackson's default camelCase output (`renditionUrls`), while `@Schema(name = "rendition_urls")` annotations documented snake_case in the OpenAPI spec. `openapi-typescript` generated snake_case TS types from the spec; the wire emitted camelCase. The web client read `media.rendition_urls` and got `undefined` (ADR-65).

## Decision
`spring.jackson.property-naming-strategy: SNAKE_CASE` globally. The spec is now accurate; TS types match the wire. Existing `@Schema(name = ...)` annotations were dropped where they were just restating the new global default.

## Rationale
- snake_case is the dominant REST convention — Stripe, GitHub, Twilio, etc. Third-party developers' tooling expects it.
- Documenting one shape and emitting another is the worst of both worlds; align them at the source.
- A single config flip is cheaper than per-field `@JsonProperty` everywhere.

## Consequences
- Lombok strips `is` prefixes from `boolean` getters: `User.isVerified` becomes wire field `verified`, not `is_verified`. Worth knowing during code review.
- Jackson's naming strategy applies to bean properties, NOT `Map<String, String>` keys — outbox payload `{"mediaId": "..."}` keys are preserved, which is exactly what we want for stable event contracts (see ADR-36).
- All TS types regenerate cleanly from the spec; future web slices don't have to fight the contract.
- Internal DTOs (cache values, queue payloads) are unaffected — those use a separate ObjectMapper (ADR-20).
