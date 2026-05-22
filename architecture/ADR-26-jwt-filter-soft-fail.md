# ADR-26: `UserJwtFilter` soft-fail on invalid token

**Status:** Accepted
**Date:** 2026-05-21 (Slice 3)
**Tags:** backend, security

## Context
`UserJwtFilter` runs on every request, including those targeting public endpoints (`GET /trending`, `GET /search`, `GET /channels/{username}`). A malformed or expired `Authorization` header on a public endpoint should not return 401 — the endpoint doesn't require auth in the first place.

## Decision
`UserJwtFilter` is "soft-fail": on any JWT validation error it clears the `SecurityContext` and continues the filter chain. It never short-circuits with 401. Authorization decisions are deferred to the chain's `AuthorizationFilter`, which 401s only when an authenticated principal is actually required.

## Rationale
- Filter responsibility is "decode credentials if present"; authorization responsibility is "require credentials where rules say so". Keeping them separate is the Spring Security convention.
- A user with a stale JWT browsing trending shouldn't see a 401 they can't act on — they should see trending.
- `DeveloperApiKeyFilter` follows the same pattern in Slice 6 — uniform mental model across both chains.

## Consequences
- Invalid tokens are silently dropped at the filter level. Clients distinguishing "I was logged in and got logged out" from "my request was anonymous" must check the response shape, not the status code.
- 401s only emerge from `AuthorizationFilter` when an endpoint actually requires authentication — clear separation of concerns.
- Endpoints that need to know whether a principal is present can read `SecurityContextHolder` directly (e.g., to personalize a public endpoint); a missing principal is a normal anonymous state, not an error.
