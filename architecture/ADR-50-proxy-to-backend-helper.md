# ADR-50: `proxyToBackend` helper for Route Handlers

**Status:** Accepted
**Date:** 2026-05-22 (Web Slice 2)
**Tags:** web, auth

## Context
Every authed Route Handler does the same five things: read the access cookie, set the `Authorization` header, forward the request to Spring with the original method + body, surface the response, and on 401 trigger a refresh-then-retry. Copy-pasting that across 15-20 handlers is the natural way to introduce subtle drift (one handler forgets the retry, another forgets to forward `Content-Type`).

## Decision
A single `proxyToBackend(req, { path, method, body? })` helper in `lib/auth/proxy.ts` handles cookie read, header forwarding, refresh-then-retry on 401, and response passthrough. Route Handlers become two-line stubs that call it.

## Rationale
- One implementation = one place to fix bugs. The Bug 7 / Bug 8 / Bug 9 pattern (similar mistakes in similar code) is exactly what this helper eliminates.
- Refresh-retry logic lives in one place — adding token rotation, jitter, or backoff later is a single-file change.
- Route Handlers stay declarative — what they proxy, not how.

## Consequences
- The helper is a critical-path piece — bugs here affect every authed call. Unit tests for it are non-negotiable (the only Route Handler logic we explicitly test).
- Non-standard proxy needs (multipart upload streaming, SSE) bypass the helper and reimplement what they need — acceptable for edge cases.
- The helper's signature is the de-facto contract for "how Route Handlers talk to Spring" — changes need a sweep of callsites.
