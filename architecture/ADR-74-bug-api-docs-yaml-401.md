# ADR-74: `/v3/api-docs.yaml` not in `permitAll` list

**Status:** Accepted
**Date:** 2026-05-21 (Slice 0, surfaced re-running `exportOpenApi`)
**Tags:** bug, security

## Context
**Symptom:** The `exportOpenApi` Gradle task (which pulls `/v3/api-docs.yaml` from a running app and writes `docs/openapi.yaml` — see Slice 0 tooling) started failing with `HTTP 401` after the Slice 3 security rewrite landed. Same task had worked before the auth slice.

Root cause: Slice 3's `SecurityConfig` rewrite added a `permitAll` list that included `/v3/api-docs/**` (matching `/v3/api-docs`, `/v3/api-docs/swagger-config`, etc.). But Spring's Ant matcher treats `/v3/api-docs.yaml` as a *sibling* path of `/v3/api-docs`, not a child — the `**` only matches children under the slash. So `.yaml` (and `.json` for that matter) fell through to `anyRequest().authenticated()` → 401.

Easy to miss because Swagger UI itself was reachable (separate `/swagger-ui/**` permit) and `/v3/api-docs` (the JSON) was also reachable. The `.yaml` flavour was the one outlier consumed only by the offline Gradle task.

## Decision
Expand the `permitAll` matcher list to include both `/v3/api-docs` and `/v3/api-docs/**` AND `/v3/api-docs.yaml`. (Some teams use a regex matcher `/v3/api-docs.*` to cover the case — we kept the explicit list for grep-ability.)

## Rationale
- Explicit > clever for security configuration. A reviewer should be able to see at a glance every public path.
- Spring's Ant-style `**` matcher's slash-boundary behaviour is a well-known footgun. The fix is to enumerate, not to switch matcher styles.
- Could have moved OpenAPI docs behind auth and had `exportOpenApi` send a token. Wrong direction — the OpenAPI doc is the public API contract; clients regenerate from it.

## Consequences
- General rule: any time a `permitAll` pattern uses `**`, add a test (or at minimum a `curl` smoke) for the sibling paths that look like they should match but don't.
- Reinforces [ADR-25](ADR-25-two-filter-chains.md) — each chain's `permitAll` list is a tiny but high-blast-radius API contract. Treat changes to it with the same care as the security config itself.
- A future "verify every documented public endpoint returns < 400 unauthenticated" smoke test would have caught this immediately. Worth bundling into the `exportOpenApi` task — the task already needs the server running.
