# ADR-36: Outbox payload = `{mediaId}` only (no embedded data)

**Status:** Accepted
**Date:** 2026-05-20 (Slice 1)
**Tags:** backend, messaging, data

## Context
The transactional outbox (ADR-16) carries events from Postgres writes to the ES indexer. The payload could either embed a snapshot of the row at write time, or carry only `{"mediaId": "<uuid>"}` and let the consumer reload from Postgres on dequeue.

## Decision
Outbox payload is `{"mediaId": "<uuid>"}` only. `MediaIndexer` reloads the row from Postgres at dequeue time and projects to the ES doc.

## Rationale
- Always-fresh state: the indexer never sees a stale snapshot. Multiple updates between write and consume collapse into one read of the latest state.
- Smaller events → faster outbox table I/O, smaller WAL, less serialization cost.
- Schema evolution is easier: changing the indexed shape doesn't require draining or replaying old payloads.
- The trade-off is one extra DB read per event; negligible at expected volume and offset by Postgres's row cache being hot.

## Consequences
- The consumer MUST be able to read the latest row at dequeue time — if the row is deleted before the indexer runs, the indexer treats it as a delete (idempotent against ES).
- Payload schema stays trivially stable across slices — no migration concerns on the bridge format.
- `Map<String, String>` payload keys survive Jackson's snake_case strategy unchanged (see ADR-34), so `OutboxPoller.extractMediaId("mediaId")` is a stable contract.
- Adding richer event types (e.g., `media.deleted` carrying tombstone metadata) is an additive payload change, not a redesign.
