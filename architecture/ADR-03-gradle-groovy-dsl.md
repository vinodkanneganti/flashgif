# ADR-03: Gradle Groovy DSL over Kotlin DSL

**Status:** Accepted
**Date:** 2026-05-20 (Slice 0)
**Tags:** backend, build

## Context
Gradle ships two build script flavours: Groovy DSL (`build.gradle`) and Kotlin DSL (`build.gradle.kts`). The backend is Java-only; no Kotlin source code is planned.

## Decision
Use Groovy DSL throughout `backend/`.

## Rationale
- Most Spring Boot tutorials, plugin docs, and Stack Overflow answers are Groovy DSL — lower friction when troubleshooting.
- Kotlin DSL's main payoff is IDE autocomplete in build scripts, which matters far more in mixed Kotlin/Java codebases.
- No Kotlin code in the project means we'd be adopting a second language purely for build files.
- Configuration cache and modern Gradle features work identically in both DSLs.

## Consequences
- Slightly weaker autocomplete in build scripts; acceptable for a small build file.
- Re-evaluate if we ever add Kotlin sources (mobile, scripting tasks). The migration is mechanical, file-by-file.
