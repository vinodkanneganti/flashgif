# ADR-17: No RabbitMQ on the indexing path (DB→ES direct via poller)

**Status:** Accepted
**Date:** 2026-05-20 (Slice 1)
**Tags:** backend, messaging, search

## Context
The outbox pattern (ADR-16) describes how events get out of Postgres. The remaining question is whether the indexing path goes Postgres → outbox → Rabbit → consumer → ES, or Postgres → outbox → poller → ES directly.

## Decision
Outbox poller writes ES directly. No RabbitMQ on the indexing path today. (Rabbit is used for the transcode queue — ADR-18 — which is a different concern.)

## Rationale
- A single consumer (the indexer) doesn't need a broker for fan-out — Rabbit would be ceremony with no payoff.
- The poller is a single Java method with backoff and batch sizing; a broker would add a moving part to monitor.
- When (if) a second consumer wants the same event stream (analytics, notifications), the poller becomes a publish-to-Rabbit step and consumers fan out from there. The migration is local to one class.

## Consequences
- Adding the second consumer is a one-step refactor when justified; until then we avoid the operational tax.
- The poller and the ES index are tightly coupled (one consumer, one path); acceptable while ES is the only reader.
- If the poller falls behind, search lag is observable directly on `outbox_events.published_at` rather than on a queue depth.
