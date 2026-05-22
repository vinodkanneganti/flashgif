# ADR-25: Two `SecurityFilterChain` beans, scoped by path

**Status:** Accepted
**Date:** 2026-05-21 (Slice 3)
**Tags:** backend, security

## Context
The system has two distinct credential types: end-user JWTs (everywhere) and developer API keys (`/api/v1/developer/**`). They have different validation filters, different rate-limit tiers, and different error responses. Mixing them in a single chain creates branchy filter code that's hard to read and easy to misconfigure.

## Decision
Two `SecurityFilterChain` beans wired with explicit `@Order` and `securityMatcher`:
- `@Order(1) developerChain` — `securityMatcher("/api/v1/developer/**")`, uses `DeveloperApiKeyFilter` + `DeveloperRateLimitFilter`.
- `@Order(2) userChain` — catch-all, uses `UserJwtFilter`.

Spring routes each request to the first matching chain.

## Rationale
- One chain per credential type means each chain reads top-to-bottom as one cohesive auth story.
- Per-chain rate limits and entry points become trivial — no per-request "which kind of caller is this?" branching.
- Order matters: developer chain first because its matcher is more specific; user chain is the catch-all.

## Consequences
- Key-management endpoints (`/api/v1/auth/developer/keys`) deliberately stay in the user chain — issuing a key is a user operation, authenticated with a JWT.
- The developer chain was wired with `denyAll()` in Slice 3 as a placeholder; Slice 6 swapped in the real API key filter. The split structure made that swap a single-bean change.
- Adding a third audience (admin, service-to-service) is a third chain, not a fork inside an existing one.
