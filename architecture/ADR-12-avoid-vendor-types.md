# ADR-12: Avoid Postgres-vendor column types (`citext`, `inet`)

**Status:** Accepted (post-bug)
**Date:** 2026-05-21 (Slice 3)
**Tags:** backend, data, ORM

## Context
Initial Users-slice schemas used `citext` for `users.email` (case-insensitive equality at the DB layer) and `inet` for `refresh_tokens.ip` (Postgres's typed IP address). Both seemed like the "right" Postgres choice. Hibernate's strict schema validator rejected both — `citext` and `inet` map to JDBC type `OTHER`, but `String` fields bind as `VARCHAR`. See ADR-61 and ADR-63 for the bug post-mortems.

## Decision
Default to portable SQL column types (`varchar`) in app-facing schemas. Handle case-insensitivity, IP-formatting, etc. at the app layer (`UserService.normalizeEmail()` lowercases on assignment). Vendor types are allowed when the value never reaches the JPA layer (e.g., a generated stats column).

## Rationale
- Vendor-specific types create friction with ORMs that assume JDBC-standard mappings.
- App-layer normalisation is one line of code per case; the bug surface from JDBC-type mismatches is much larger.
- Portable schemas keep tests, ORMs, and any future replication tooling happier.
- The performance argument for `citext` over `LOWER(email)` indexes doesn't matter at our scale.

## Consequences
- `users.email` is `varchar(254)` with mandatory lowercase normalisation in `UserService`.
- `refresh_tokens.ip` is `varchar(45)` (IPv6 max, with zone identifier).
- No `citext`/`inet`-specific operators available; fine for our patterns.
- Any future "let's use the cool Postgres type" suggestion has to clear the JDBC-type-mismatch bar.
