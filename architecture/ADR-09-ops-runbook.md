# ADR-09: Ops runbook in `ops/instructions.md`

**Status:** Accepted
**Date:** 2026-05-20 (Slice 0)
**Tags:** ops, docs

## Context
Compose, env vars, credentials, ports, health checks, and backup procedures all need a single home an ops/SRE handoff can use without re-reading the build log. Options were repo root, a `docs/` directory, or beside the compose file.

## Decision
Self-contained runbook at `ops/instructions.md`. Covers prerequisites, ports, credentials, start/stop/wipe commands, per-service health checks, backup, env-var overrides for remote hosts, and an explicit "not suitable for production" section pointing to managed equivalents.

## Rationale
- Living next to `docker-compose.yml` keeps the runbook and the thing-it-documents in the same diff when something changes.
- Repo root stays focused on `CLAUDE.md` / `progress.md` / `prd.txt` — the things every contributor reads.
- An explicit "not for prod" section heads off the obvious mistake before someone tries it.

## Consequences
- Any compose change requires a runbook update — reviewers enforce.
- Production deployment docs live separately (per environment) and link back to the runbook for the conceptual model.
- New external services (e.g., when we add Bucket4j-Redis) land in compose + the runbook in the same PR.
