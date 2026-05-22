# ADR-24: Refresh token rotation chain via `replaced_by`

**Status:** Accepted
**Date:** 2026-05-21 (Slice 3)
**Tags:** backend, security

## Context
Refresh tokens rotate on every `/auth/refresh` call (the old token is single-use). The naive shape — delete-old, insert-new — loses the relationship between rotations, which makes compromised-token detection impossible after the fact.

## Decision
`refresh_tokens` has a self-referential `replaced_by` FK. On rotation:
1. Insert the new token row.
2. Mark the old row revoked and set `old.replaced_by = new.id`.

The chain `token_n → token_n-1 → … → token_0` is preserved for audit.

## Rationale
- A linked rotation chain is the standard way to detect refresh-token reuse: if a revoked token is presented again, walk the chain and invalidate every descendant — the attacker has a stale copy and the legitimate session is compromised.
- Self-FK is one nullable column; the cost is trivial compared to the forensic value.
- Doesn't change the hot path — only `/auth/refresh` writes the link.

## Consequences
- `refresh_tokens` grows unbounded per active user (one row per rotation over the 30-day TTL). A scheduled cleanup job for rows past `expires_at + grace` is the obvious follow-up.
- Reuse detection logic isn't wired in v1 — the data is there waiting. When it lands it's a query, not a schema change.
- Schema-level FK on `replaced_by` references the same table; `ON DELETE SET NULL` keeps cleanup safe.
