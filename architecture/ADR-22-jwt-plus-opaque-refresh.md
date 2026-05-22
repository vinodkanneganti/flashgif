# ADR-22: JWT access + opaque refresh (mixed mode)

**Status:** Accepted
**Date:** 2026-05-21 (Slice 3)
**Tags:** backend, security

## Context
Slice 3 needed a token model that gave every request stateless validation (no DB hit on the hot path) AND allowed cheap revocation on account compromise. Pure-JWT requires a blacklist to revoke; pure-opaque requires a DB lookup on every authenticated request.

## Decision
Two-token model:
- **Access token** — HS256 JWT, 15-min TTL, signed with server secret. Validated locally by `UserJwtFilter`; no DB hit.
- **Refresh token** — 256-bit opaque random, SHA-256 hashed in `refresh_tokens` (see ADR-23), 30-day TTL, rotated on every `/auth/refresh` (see ADR-24).

## Rationale
- JWT for access keeps the hot path stateless. Most requests don't touch the auth DB.
- Opaque refresh lets us revoke a session by deleting (or marking revoked) a single row — no JWT blacklist that would defeat the stateless benefit.
- "Mixed mode" is the textbook pattern from Auth0, Stripe, etc.; we're not inventing anything.

## Consequences
- Revocation lag = at most 15 minutes (the access TTL). Acceptable for v1.
- If we later need instantaneous revocation (admin ban, leaked token), add a Redis deny-list keyed on `jti` and check it in `UserJwtFilter` — the structure already supports this without changing the model.
- Two storage formats (JWT claims vs DB row) — but each is the simplest tool for its job.
