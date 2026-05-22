# ADR-58: Targeted Playwright coverage (no exhaustive matrix)

**Status:** Accepted
**Date:** 2026-05-22 (Web Slice 1)
**Tags:** web, testing

## Context
Playwright is wired up (ADR-48), but "test everything" is a trap — every spec is a few seconds of CI time and a flake risk. The cheap-to-maintain coverage is the happy path of each slice's primary flow; the expensive-to-maintain coverage is every error state of every component.

## Decision
Each web slice ships with one or two specs covering the slice's primary happy path end-to-end. No exhaustive matrix of error states, no per-component visual regression, no cross-browser run in CI. The CI run stays under a minute.

## Rationale
- Happy-path specs catch the integration bugs that matter: did the form submit, did the API call go out, did the cache update, did the UI re-render. That's where regressions actually live.
- Error-state coverage at the e2e layer is expensive (requires backend stubbing or fault injection) and low-yield — unit tests at the component layer cover those for free.
- A fast CI run gets run on every push; a slow one gets skipped or batched.

## Consequences
- Bugs at the boundaries of the happy path (rare error responses, edge-case input handling) are caught by manual smoke or production telemetry, not by CI.
- Adding cross-browser coverage is a deliberate later step (when we ship to broader audiences or get a Safari-specific bug).
- New web features get one spec per slice — easy rule, easy to audit in code review.
