# ADR-08: Local infra via docker compose

**Status:** Accepted
**Date:** 2026-05-20 (Slice 0)
**Tags:** ops, local-dev

## Context
The backend depends on five stateful services (Postgres, Elasticsearch, Redis, RabbitMQ, MinIO). Developers need to spin them up consistently for `bootRun` and ad-hoc work. Testcontainers covers the test classpath but isn't designed to back interactive development.

## Decision
`ops/docker-compose.yml` brings up all five services with pinned tags (Postgres 16.4, Elasticsearch 8.15, Redis 7.4, RabbitMQ 3.13-mgmt, MinIO latest). Each service has a healthcheck; volumes mount under `ops/data/` (gitignored). Testcontainers stays for tests.

## Rationale
- Compose is the lowest-friction way to give every dev the same stack — one command, one file under source control.
- Pinned image tags prevent silent drift when someone re-pulls months later.
- Compose for `bootRun` + Testcontainers for tests is two different jobs with two different tools; trying to share is more pain than parallel maintenance.
- Healthchecks let `depends_on: condition: service_healthy` work, so the app waits for real readiness, not just container start.

## Consequences
- Devs need Docker (or Colima/Podman) installed; documented in the ops runbook.
- Five containers idle ~1.5 GB RAM — acceptable on modern laptops.
- Production uses managed equivalents (RDS, Elastic Cloud, ElastiCache, Amazon MQ, S3) — compose is never a prod artifact.
