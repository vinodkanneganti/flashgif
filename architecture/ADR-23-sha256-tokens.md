# ADR-23: SHA-256 (not BCrypt) for high-entropy tokens

**Status:** Accepted
**Date:** 2026-05-21 (Slice 3)
**Tags:** backend, security

## Context
Refresh tokens (ADR-22) and developer API keys (Slice 6) are server-generated, 256-bit `SecureRandom` values. Standard "hash before storage" reflex says BCrypt — but BCrypt exists to slow down brute-force attacks against low-entropy human-chosen passwords.

## Decision
Store `SHA-256(rawToken)` as `bytea`. Lookups are constant-time hash-equality on a unique index. Same algorithm in both `refresh_tokens.token_hash` and `developer_keys.key_hash`.

## Rationale
- BCrypt's adaptive cost (~100ms / verify) is wasted on a 256-bit secret. Brute-forcing a uniform 256-bit value is infeasible regardless of hash function.
- SHA-256 is deterministic, so lookup is a single indexed equality predicate — no scan-all-rows-and-verify dance.
- One algorithm in two places keeps the mental model uniform.

## Consequences
- Token verification is microseconds, not 100ms — meaningful on the hot path for API keys.
- If an attacker dumps the DB, raw tokens are still not recoverable (preimage of SHA-256 is hard); only same-input lookups work, which is exactly the operation we want.
- Do NOT reuse this pattern for user passwords — those are low-entropy and MUST use BCrypt (or Argon2). The "high-entropy" qualifier in the title matters.
