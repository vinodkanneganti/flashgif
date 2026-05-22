# ADR-61: `citext` rejected by Hibernate schema validator

**Status:** Accepted
**Date:** 2026-05-21 (Slice 3)
**Tags:** bug, data, ORM

## Context
**Symptom:** App boot failed schema validation with `found [citext (Types#OTHER)], but expecting [varchar(255) (Types#VARCHAR)]` on `users.email`.

V4 created `users.email` as Postgres `citext` for case-insensitive equality at the DB layer. With `spring.jpa.hibernate.ddl-auto: validate` (see [ADR-11](ADR-11-flyway-owns-schema.md)), Hibernate compared the live column type against the entity field's expected JDBC type. `String` defaults to `VARCHAR`; `citext` reports as `OTHER` (JDBC code 1111). Mismatch → context refuses to start.

## Decision
Drop `citext` entirely. V5 `ALTER COLUMN email TYPE varchar(254)`. `User.setEmail` lowercases on assignment; `UserService.normalizeEmail` is the single source of truth for the lookup form; `UserService.findByEmail` wraps the repo call. `AuthService.login` and every other call site goes through the wrapper.

## Rationale
- Two viable fixes existed: (a) annotate the field with `@JdbcTypeCode(SqlTypes.OTHER)` to make Hibernate accept the vendor type, or (b) drop the vendor type and normalise in app code. Picked (b) because it removes a future-portability landmine and is one extra line per write path.
- The performance argument for `citext` over a `LOWER(email)` index doesn't matter at our scale.
- Tied with [ADR-63](ADR-63-bug-inet-hibernate.md), this bug is what triggered the broader [ADR-12](ADR-12-avoid-vendor-types.md) decision to avoid Postgres-vendor column types.

## Consequences
- Email normalisation is now an app invariant — anyone bypassing `UserService` (e.g., raw SQL admin tool) needs to lowercase manually.
- All future "let's use the cool Postgres type" suggestions have to clear the JDBC-type-mismatch bar. See [ADR-12](ADR-12-avoid-vendor-types.md).
- A Testcontainers integration test that started the Spring context against a real Postgres would have caught this at `./gradlew build` time. The deferral cost a real-world bug; tracked as debt in [ADR-57](ADR-57-defer-testcontainers.md).
