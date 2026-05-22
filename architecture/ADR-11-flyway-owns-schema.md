# ADR-11: Flyway-owned schema; `ddl-auto: validate`

**Status:** Accepted
**Date:** 2026-05-20 (Slice 0)
**Tags:** backend, data, migration

## Context
Hibernate can generate or update schemas at boot (`ddl-auto: create | update`). That's convenient in tutorials and lethal in production — silent drift, unreversible changes, no audit trail.

## Decision
Flyway is the single authority for schema changes. `spring.jpa.hibernate.ddl-auto: validate` — Hibernate verifies that the entity model matches the live schema and fails fast if not. `V1__baseline.sql` starts intentionally empty; real migrations land per slice (`V2__media_and_outbox.sql`, etc.).

## Rationale
- Migrations are versioned, ordered, and reviewable; `ddl-auto: update` is none of those.
- `validate` mode catches entity/schema mismatches at boot rather than at first query — the cheapest possible failure mode.
- One mechanism for schema change keeps prod rollouts predictable: same SQL ran in dev, ran in staging, runs in prod.

## Consequences
- Every model change requires a migration file — slight overhead, large payoff in safety.
- Entity-only changes that don't touch the DB still need to validate against the live schema; renaming a column requires the migration first.
- Schema validation has caught real bugs already (citext/inet column-type mismatches surfaced at boot — see ADR-61, ADR-63).
