# ADR-71: Transcode dispatch race (publish before tx commit)

**Status:** Accepted
**Date:** 2026-05-23 (Backend Slice 2, latent — surfaced in Web 3)
**Tags:** bug, concurrency

## Context
**Symptom:** Immediately after [ADR-70](ADR-70-bug-s3-key-not-null.md) was fixed, `TranscodeWorker` threw `IllegalStateException: Illegal upload state transition: AWAITING_UPLOAD → PROCESSING`.

Root cause: `UploadService.markUploaded` is `@Transactional` and called `rabbitTemplate.convertAndSend(...)` *inside* the transaction, after the entity save but before commit. RabbitMQ delivered the message synchronously enough that the `TranscodeWorker` — same JVM, separate thread, separate transaction — pulled it and SELECTed the upload row before the original transaction had committed. The worker read stale `AWAITING_UPLOAD` state, tried to transition to `PROCESSING`, and the state machine (correctly) rejected the transition.

Textbook "publish before commit" race. Easy to miss because in single-broker, same-JVM setups the timing window is microseconds — but RabbitMQ closes it tightly enough to lose every time.

## Decision
Wrap the dispatch in `TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { afterCommit() { rabbitTemplate.convertAndSend(...); } })`. Spring fires the callback after the JDBC commit succeeds. The worker now sees the committed `UPLOADED` state, and the transition is legal.

## Rationale
- Publish-after-commit is the textbook pattern for "external system notification from a transactional service method". It's the simplest correct option here.
- Could have moved the publish out of the `@Transactional` method into a wrapper. Works, but distributes the "be careful about ordering" concern across every call site.
- Could have routed transcode through the outbox (same mechanism as ES indexing — see [ADR-16](ADR-16-transactional-outbox.md)). We deliberately chose NOT to (see `architecture.md` §5.3, "RabbitMQ direct path") — outbox + poller for ES indexing makes sense because the consumer is local; the transcode worker is also local but the RabbitMQ queue gives us back-pressure for free, which the outbox doesn't.
- If we add more "publish to external system from a service method" sites, generalising via Spring `@TransactionalEventListener(phase = AFTER_COMMIT)` would be cleaner than ad-hoc registrations.

## Consequences
- Promoted into [ADR-35](ADR-35-publish-after-commit.md) as the project-wide "publish after commit" rule. Every dispatch site is grepable for `afterCommit` (or `@TransactionalEventListener`); a dispatch without it is a bug.
- Sibling bug to [ADR-70](ADR-70-bug-s3-key-not-null.md). Both were Slice 2 latent. See its cumulative-latent-bug note.
