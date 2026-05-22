# ADR-18: RabbitMQ over Kafka for the transcode queue

**Status:** Accepted
**Date:** 2026-05-21 (Slice 2)
**Tags:** backend, messaging

## Context
The transcode pipeline needs an async queue between the upload-complete handler (producer) and the FFmpeg workers (consumer pool). The decision is RabbitMQ vs Kafka. Workload: single producer, single consumer pool, ≤10/s sustained throughput, no replay requirements, work items take 5-60s each.

## Decision
RabbitMQ. Direct exchange `media.transcode` → durable queue `media.transcode.requests` → DLQ via `x-dead-letter-exchange="" + x-dead-letter-routing-key`. Persistent messages, `prefetch=1`, `default-requeue-rejected=false`.

## Rationale
- Kafka shines at high throughput, long-term replay, and many-consumer fan-out. None apply here.
- RabbitMQ is operationally simpler — one broker, one queue, no partition planning, no consumer-group coordination.
- Spring AMQP integration is mature; `@RabbitListener` + the message converter is ~5 lines of consumer code.
- DLQ + poison-message handling are first-class in Rabbit; in Kafka they require an extra topic + consumer.
- `prefetch=1` matches CPU-bound FFmpeg work — no one worker hogs the queue.

## Consequences
- If we ever need event-sourced workflows (user activity → analytics + recommendations + notifications) we'll add Kafka alongside Rabbit, not replace it. Different tools for different shapes of work.
- DLQ monitoring is a runbook task (alarm on non-empty `media.transcode.dlq`).
- RabbitMQ on the production side is a managed offering (Amazon MQ or CloudAMQP), same protocol as local.
