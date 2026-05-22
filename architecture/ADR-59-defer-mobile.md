# ADR-59: Defer iOS / Android clients

**Status:** Deferred
**Date:** 2026-05-22 (Web Slice 1)
**Tags:** mobile, scope

## Context
The PRD lists iOS (Swift + SwiftUI) and Android (Kotlin + Jetpack Compose) clients alongside the web. Building all three platforms in parallel triples the contract-drift surface (every API change has to be reflected in three codebases) and divides team attention across very different toolchains.

## Decision
Defer both mobile clients until the web is feature-complete and the API contract is stable. The OpenAPI spec at `docs/openapi.yaml` is the artifact mobile teams consume when they start; client codegen via Swift OpenAPI Generator + Kotlin OpenAPI Generator is the planned path.

## Rationale
- One client at a time means one contract-consumer's feedback loop pressuring the API into a clean shape — three would mean compromise-by-committee.
- The web client surfaces backend gaps faster (browser is the easiest dev loop) and shakes out the contract before mobile teams build against it.
- Mobile codegen + cookie-less auth (mobile gets the raw JWT, not the httpOnly cookie) is a meaningfully different integration story — better to design once the patterns are settled.

## Consequences
- Mobile presence is delayed; acceptable for v1.
- The OpenAPI spec is being kept honest (ADR-34 / snake_case alignment was the latest correction) precisely because mobile codegen depends on it.
- When mobile starts: each platform gets its own slice plan, a client-codegen build step, and a mobile-specific auth design (PKCE + JWT in keychain / EncryptedSharedPreferences).

## Trigger to revisit
Web is feature-complete (all six PRD features shipped) AND a mobile-first usage signal emerges (analytics, partner ask, App Store strategic need).
