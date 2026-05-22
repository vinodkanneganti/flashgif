# ADR-01: Modular monolith over microservices

**Status:** Accepted
**Date:** 2026-05-20 (Slice 0)
**Tags:** backend, architecture

## Context
Greenfield codebase; team of ~1–3; six bounded contexts in the PRD (search, media, users, favorites, channels, developer). The shape of the system (microservices vs monolith vs serverless) sets every other decision downstream.

## Decision
Single Spring Boot deployable with strict package-by-feature boundaries (`com.flashgif.search`, `…media`, `…users`, `…favorites`, `…channels`, `…developer`, `…infra`). Modules talk only via service interfaces. No cross-module repository access.

## Rationale
- Microservices day-one would pay all the operational tax (six deploy targets, runbooks, dashboards, distributed tracing, schema-evolution coordination) for none of the scaling benefit at our size.
- Module boundaries inside one process give us the extraction option without the cost.
- The pain of a wrong domain split is much cheaper to fix inside one repo than across six.

## Consequences
- Single CI build, single deploy, single observability surface.
- Module hygiene relies on code review + (later) ArchUnit, not network boundaries.
- When load justifies extraction we have natural seams (transcode worker is the obvious first candidate) without refactoring domains.

## Trigger to revisit
Any single module needs >10× the deployment cadence of the others, OR its scaling profile diverges (GPU for transcode; isolated rate-limit infra for dev API).
