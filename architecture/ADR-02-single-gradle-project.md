# ADR-02: Single Gradle project, package-by-feature

**Status:** Accepted
**Date:** 2026-05-20 (Slice 0)
**Tags:** backend, build

## Context
ADR-01 chose a modular monolith. The next question is how to express the module boundaries in the build: Gradle subprojects (one per module) or a single Gradle project with package-by-feature inside `com.flashgif.*`.

## Decision
Single Gradle project (`backend/`). Module boundaries are package-level (`com.flashgif.search`, `…media`, `…users`, `…favorites`, `…channels`, `…developer`, `…infra`). Each package owns a `package-info.java` declaring its responsibility and dependency rules.

## Rationale
- Gradle subprojects pay for build orchestration we don't need at six tiny modules — slower configuration, slower IDE import, more `build.gradle` files to keep in sync.
- The whole point of a modular monolith is one deployable; one Gradle project matches that shape.
- Module hygiene is a code-review concern, not a build concern. ArchUnit can enforce it later if review starts slipping.
- Splitting to subprojects later is mechanical if a module ever needs its own artifact.

## Consequences
- Single CI build, single JAR, single test classpath.
- No build-time prevention of cross-module repository access; reviewers (and future ArchUnit) catch it.
- If we ever extract a module to its own artifact, we lift its package into a new subproject — no domain refactor required.
