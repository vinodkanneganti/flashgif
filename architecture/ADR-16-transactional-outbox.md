# ADR-16: Transactional outbox over CDC

**Status:** Accepted
**Date:** 2026-05-20 (Slice 1)
**Tags:** backend, data, messaging

## Context
Postgres is the system of record (ADR-10); Elasticsearch is the read-only secondary (ADR-13). Need to bridge writes between them without the dual-write problem. The canonical options are change-data-capture (Debezium + Kafka Connect tailing the WAL) or the transactional outbox pattern (application writes an outbox row in the same transaction; a poller drains it).

## Decision
Transactional outbox. App writes an `outbox_events` row in the same JDBC transaction as the domain change. `OutboxPoller` (`@Scheduled` 2s) drains unpublished rows and writes to ES.

## Rationale
- Outbox is operationally simpler than Debezium + Kafka Connect — no separate connector cluster, no WAL replication slot to manage.
- Application-level control over event shapes; CDC gives you row diffs, which is a different problem.
- Portable across databases — Postgres, MySQL, anything with transactional inserts.
- CDC's payoff shows up at very large scale or when many independent consumers want the same event stream; we have neither.

## Consequences
- One scheduled task to monitor (lag metric on `outbox_events.created_at` vs `published_at`).
- At-least-once delivery — consumers must be idempotent. ES upserts keyed by `mediaId` are.
- Outbox table is append-only; archival/cleanup is a deferred runbook concern (delete published rows older than N days).

## Trigger to revisit
A second consumer needs the same event stream (analytics, notifications) AND combined throughput exceeds the poller's batch capacity — at that point Debezium + Kafka starts to look attractive.
