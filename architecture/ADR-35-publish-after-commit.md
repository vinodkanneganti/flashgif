# ADR-35: Publish-after-commit for async dispatch

**Status:** Accepted (post-bug; see ADR-71)
**Date:** 2026-05-21 (Slice 2, codified post-Bug 71)
**Tags:** backend, concurrency, messaging

## Context
`UploadService.markUploaded` was `@Transactional` and called `dispatcher.dispatch(...)` to publish a RabbitMQ message inside the transaction. With the consumer in the same JVM, Rabbit delivered the message before the JDBC commit completed — the consumer read the upload row and saw stale state. The result was a race that flickered into existence under load (ADR-71).

## Decision
Any service method publishing to RabbitMQ (or any external system) from inside a `@Transactional` boundary wraps the dispatch in `TransactionSynchronizationManager.registerSynchronization(...) { afterCommit() }`. The publish only fires after the JDBC commit succeeds.

## Rationale
- "Publish after commit" is the textbook pattern for fire-and-forget side effects on writes. Consumers see committed state, full stop.
- Don't pretend Rabbit is part of the DB transaction — it isn't, and 2PC is the wrong answer at our scale.
- The synchronization hook is built into Spring; no new infrastructure.

## Consequences
- One pattern, applied uniformly. Any new publish-from-transaction site MUST use it — caught in code review until we generalize.
- If the publish itself fails after commit, the DB state has moved on and we have a lost message. Outbox covers durability where we need it (search index); for transient dispatch we accept this risk and rely on retry.
- If we add three or more dispatch sites, generalize via `@TransactionalEventListener(AFTER_COMMIT)` — cleaner than copying the synchronizer call.
