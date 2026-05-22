# ADR-57: Defer Testcontainers integration tests (debt)

**Status:** Deferred (debt)
**Date:** 2026-05-20 (Slice 0)
**Tags:** backend, testing, debt

## Context
Unit tests cover the easy stuff — pure functions, service logic with mocked repos. The bugs that actually bite are at the integration boundaries: Postgres column types Hibernate rejects, RabbitMQ publish racing tx commit, S3 bucket policy missing, Elasticsearch mapping drift. Testcontainers-backed integration tests would catch most of them at `./gradlew build` time.

## Decision
Defer Testcontainers wiring until a slice without a feature deadline. Document the debt visibly in every bug post-mortem so the cost stays in front of us.

## Rationale
- Initial velocity over correctness investment — six backend slices in 7 days proves the velocity hypothesis was viable.
- Testcontainers has a non-trivial setup cost (Docker-in-CI, test base classes, fixture management) that's hard to justify on day one before any module is concrete.
- The debt is *visible* — every bug ADR (B-prefix in the index) is a tick mark against this decision.

## Consequences
- At least **nine production-relevant bugs** in the backend slices alone (Bugs 1-9 in progress.md, ADRs 60-74) would have been caught by integration tests. This is the largest unpaid liability in the system.
- Every new backend feature ships with the same risk profile until this is paid down.
- Pay-down order: Flyway-validated schema (catches column-type bugs), Spring slice tests with Testcontainers Postgres + Rabbit + ES + MinIO, then promote the highest-value integration scenarios (upload pipeline end-to-end, auth chain).

## Trigger to revisit
As soon as no feature is mid-flight. This is the next non-feature investment.
