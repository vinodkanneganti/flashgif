# ADR-48: Playwright for e2e from day one

**Status:** Accepted
**Date:** 2026-05-22 (Web Slice 1)
**Tags:** web, testing

## Context
The backend deferred Testcontainers tests (ADR-57) and paid for it — the running bug tally proves it. For the web client we have the chance to install integration testing before the codebase accumulates regressions. The alternatives are unit-only (Vitest + RTL) or Cypress; Playwright is the modern, batteries-included default.

## Decision
Playwright wired into the web project from Slice 1. Auto-starts the dev server, runs in chromium-only in CI for speed (cross-browser run is opt-in), specs live in `web/tests/e2e/`.

## Rationale
- E2E catches the integration bugs that unit tests miss — wiring between RHF + the mutation hook + the Route Handler + Spring is exactly the surface that breaks.
- Playwright's auto-start dev server eliminates the orchestration friction (no manual "run the server in another terminal" instructions).
- Chromium-only CI keeps the run under a minute; we add browsers only for visual regression work later.

## Consequences
- Every web slice ships with at least one happy-path spec (per ADR-58: targeted, not exhaustive).
- The dev-server auto-start coupling means flaky tests on slow CI runners — bounded by the Playwright timeout config.
- E2E doesn't replace unit tests for component logic; it does replace manual smoke checks for full flows.
