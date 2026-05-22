# ADR-10: PostgreSQL as system of record

**Status:** Accepted
**Date:** 2026-05-20 (Slice 1)
**Tags:** backend, data

## Context
Need ACID for user accounts, media metadata, refresh tokens, and outbox events. JSONB fields (`rendition_urls`, `social_links`) are useful but not the dominant pattern. Relational integrity (FK cascades on user/media delete) matters across nearly every module.

## Decision
Postgres 16 + Flyway migrations + Spring Data JPA. Single primary, no replicas at v1. JSONB columns where the shape is genuinely dynamic; relational columns everywhere else.

## Rationale
- A document store would force manual integrity or 2PC-equivalents for the cross-entity guarantees we rely on (refresh-token chains, favorites→media counters, upload→media transitions).
- Postgres handles our anticipated scale (100M media rows) comfortably on a single primary with appropriate indexes.
- JSONB gives us schemaless escape hatches (rendition URL maps, social link blobs) without giving up ACID for the rest.
- Spring Data JPA is the path of least resistance with Spring Boot 3.

## Consequences
- Schema evolution requires forward-compatible migrations (expand/contract pattern when columns change shape).
- Read replicas + connection pool tuning are the natural first scaling lever before any kind of sharding.
- JSONB column access uses Hibernate 6's `@JdbcTypeCode(SqlTypes.JSON)` — straightforward but worth knowing.
