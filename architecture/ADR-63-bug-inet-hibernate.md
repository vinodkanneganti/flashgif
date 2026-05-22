# ADR-63: `inet` rejected by Hibernate at INSERT time

**Status:** Accepted
**Date:** 2026-05-21 (Slice 3)
**Tags:** bug, data, ORM

## Context
**Symptom:** After [ADR-62](ADR-62-bug-double-registered-filter.md) was fixed, `POST /auth/login` returned 500. Stack trace: `column "ip" is of type inet but expression is of type character varying`. JJWT issuance and BCrypt verify both succeeded; the failure was the `INSERT INTO refresh_tokens` because the `ip` column was declared `inet` in V4 but `RefreshToken.ip` is a `String` field that Hibernate binds as VARCHAR.

Unlike [ADR-61](ADR-61-bug-citext-hibernate.md) (`citext`), this one didn't fail schema validation at boot — `inet` happens to pass Hibernate's schema check for `String` fields in some configurations, but Postgres still refuses the implicit `varchar → inet` cast at INSERT time. So it surfaced only when an actual login wrote a token row.

## Decision
Drop the vendor type. V6 `ALTER COLUMN ip TYPE varchar(45)` (long enough for IPv6 with zone identifier). `RefreshToken.ip` keeps its default String mapping. No app-layer normalisation needed — the IP comes straight from the request and is used only for audit/forensics, not for equality lookups.

## Rationale
- Same family as [ADR-61](ADR-61-bug-citext-hibernate.md): vendor-specific types create JDBC-type friction that's not worth the marginal correctness win.
- Could have annotated `RefreshToken.ip` with `@JdbcTypeCode(SqlTypes.OTHER)` and added an `InetAddress` Postgres-specific binder — rejected as more code for a field we never query by.
- `varchar(45)` accommodates IPv6-with-zone (`fe80::1%en0`) without committing to a typed representation.

## Consequences
- Two vendor-type bugs in one slice promoted the lesson into [ADR-12](ADR-12-avoid-vendor-types.md) — default to portable SQL types unless there's a strong reason to use a Postgres-specific one.
- This bug was masked by [ADR-62](ADR-62-bug-double-registered-filter.md) (filter double-registration blocked all POSTs with 403, so login never reached the INSERT). Fix one, the next surfaces — classic.
- A Testcontainers test that POSTed to `/auth/login` against real Postgres would have caught this immediately. Same debt as [ADR-61](ADR-61-bug-citext-hibernate.md). See [ADR-57](ADR-57-defer-testcontainers.md).
