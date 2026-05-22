# ADR-62: `@Component Filter` double-registered → POST 403

**Status:** Accepted
**Date:** 2026-05-21 (Slice 3)
**Tags:** bug, security

## Context
**Symptom:** All GETs worked (`/trending`, `/search`, `/actuator/health` all 200). Every POST to a `permitAll` endpoint returned `403` with an empty body — including `/auth/login` and `/auth/register`. Classic CSRF-block signature even though `.csrf(csrf -> csrf.disable())` was in the chain.

Root cause: `UserJwtFilter` was annotated `@Component` AND added via `addFilterBefore(...)` in the security chain. Spring Boot's servlet auto-config registers any `Filter` bean as a top-level servlet filter, so the filter ran twice — once outside Spring Security (as a servlet filter) and once inside the security chain. The double pass left the request in a state that Security 6's default authentication entry point classified as forbidden.

## Decision
Five hardening changes landed together:
1. `FilterRegistrationBean<UserJwtFilter>` with `setEnabled(false)` to suppress servlet auto-registration. Filter now runs exactly once, inside the security chain.
2. Filter position anchor changed from `UsernamePasswordAuthenticationFilter.class` (only present with form login) to `AuthorizationFilter.class` (always present).
3. Explicit `HttpStatusEntryPoint(UNAUTHORIZED)` so unauthenticated requests return 401, not Security 6's default 403.
4. Switched disable calls to method references (`AbstractHttpConfigurer::disable`) and explicitly disabled `formLogin`, `httpBasic`, `logout`.
5. Security DEBUG logging in `application-local.yml` — temporary; remove once stable.

## Rationale
- `@Component` on a `Filter` subtype is a footgun in Spring Boot — the servlet container picks it up regardless of how the security chain wires it. The `FilterRegistrationBean(... setEnabled(false))` idiom is the documented escape hatch.
- Pinning the filter position to `AuthorizationFilter` is the form-login-agnostic anchor; the previous anchor existed only by accident.
- The 401-not-403 entry point matches API conventions (and what every JS client expects to trigger a token refresh).

## Consequences
- Every future custom `Filter` we add as a Spring bean must also be registered with `setEnabled(false)`. Slice 6's `DeveloperApiKeyFilter` + `DeveloperRateLimitFilter` followed the same pattern explicitly because of this bug.
- This bug masked [ADR-64](ADR-64-bug-error-dispatch-hijack.md) (error-dispatch hijack) and [ADR-63](ADR-63-bug-inet-hibernate.md) (inet type mismatch) — each fix uncovered the next. Documented as the cause→effect chain in the Slice 3 retrospective in `progress.md`.
- See [ADR-25](ADR-25-two-filter-chains.md) for the broader two-chain security model.
