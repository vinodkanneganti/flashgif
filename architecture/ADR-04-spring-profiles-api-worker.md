# ADR-04: Spring profiles for API vs Worker (not separate JARs)

**Status:** Accepted
**Date:** 2026-05-20 (Slice 0)
**Tags:** backend, deploy

## Context
The system has two runtime roles: the HTTP API and the FFmpeg transcode worker (Rabbit consumer). The build could ship two JARs (separate `main` classes) or one JAR that specialises by Spring profile.

## Decision
Single JAR. Default profile runs both API listeners and Rabbit consumers (simplest local dev). Production deploys can pass `--spring.profiles.active=api` or `worker` to specialise an instance.

## Rationale
- One artifact, one CI pipeline, one set of dependencies — half the operational surface of two JARs.
- Local dev wants both halves running in one `bootRun` — separate JARs would force two terminals.
- Spring profiles are first-class for exactly this pattern (conditional bean wiring per role).
- Splitting out the worker JAR later (e.g., for a GPU-capable image) is straightforward — flip a few `@Profile` annotations.

## Consequences
- The default-profile dev experience runs everything, which is what we want.
- Production deploys must remember to set the profile on each pod; documented in the ops runbook.
- Bean wiring that's role-specific must be gated by `@Profile` — easy to forget. Reviewers watch for it.
