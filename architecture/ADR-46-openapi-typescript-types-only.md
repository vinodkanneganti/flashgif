# ADR-46: `openapi-typescript` types only (no client codegen)

**Status:** Accepted
**Date:** 2026-05-22 (Web Slice 1)
**Tags:** web, contract

## Context
Need TypeScript types for backend response shapes so the client compiles cleanly against the API contract. Options: a full client generator (Orval, openapi-fetch, openapi-generator) that produces hooks + validators + runtime stubs, or `openapi-typescript` which only emits a `paths` / `components.schemas` type tree.

## Decision
`openapi-typescript` to generate `lib/api/types.ts` from `docs/openapi.yaml`. We hand-write thin `fetch` wrappers (`apiFetch`, `authedFetch`) and endpoint functions on top, importing the generated types.

## Rationale
- Full client generators ship hooks, validators, and runtime helpers — more dep surface, harder to customise (especially around our cookie-proxy auth model in `authedFetch`).
- Types-only keeps the generator's output trivially reviewable — it's a single `.d.ts`-shaped file with no runtime cost.
- Hand-written endpoint functions give us readable callsites (`api.search({ q })`) instead of `paths['/search']['get']`-style indexing at every callsite.

## Consequences
- Two-stage type-safety: openapi types describe the wire format; our hand-written endpoint functions describe what we actually call. Drift between the two surfaces when generated types fail to assign.
- When the backend changes the contract, regenerating types breaks the build at the endpoint function — a feature, not a bug.
- We're on the hook for writing one endpoint function per API call, but they're three-line wrappers.
