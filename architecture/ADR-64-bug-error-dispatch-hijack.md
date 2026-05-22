# ADR-64: `/error` dispatch intercepted by auth entry point

**Status:** Accepted
**Date:** 2026-05-21 (Slice 3)
**Tags:** bug, security

## Context
**Symptom:** Even after [ADR-62](ADR-62-bug-double-registered-filter.md) fixed the 403-on-POST bug, every error response — including legitimate 400s, 404s, and 500s from controllers — came back as an empty `401`. Real status codes and JSON error bodies disappeared.

Spring Boot's error handling forwards any 4xx/5xx response to `/error` for `BasicErrorController` to render. That forward is an internal `DispatcherType.ERROR` dispatch, but the path `/error` is still authorisation-checked by the security chain. The catch-all `anyRequest().authenticated()` rule was matching the internal dispatch, finding no auth, and overwriting the original status/body with the entry point's empty 401.

## Decision
Add `dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()` as the **first** rule in the user chain. Internal ERROR dispatches now flow straight through to `BasicErrorController` and render proper JSON error responses.

## Rationale
- Whitelisting `/error` by path would work for the default error path but breaks if anyone changes `server.error.path`. Matching on dispatcher type is the principled fix.
- Disabling Spring's error dispatcher entirely (custom error controller, etc.) was the alternative — way more code for no real win; `BasicErrorController`'s output is fine.
- The rule has to be first; otherwise the catch-all matches before the dispatcher rule has a chance.

## Consequences
- Always-on rule for every `SecurityFilterChain` we build. Slice 6's developer chain also has it. New chains MUST include it.
- This bug was masked by [ADR-62](ADR-62-bug-double-registered-filter.md) (POSTs returned 403 with empty body, which "looked" exactly like the symptom this bug would later show on every error). The fix-uncovers-next-fix chain through Slice 3 is documented in `progress.md`.
- Reminder that "Spring's internal request lifecycle" includes more entry points than just the original HTTP request — error forwarding, async dispatch, and async error dispatch are all distinct `DispatcherType`s that filters see.
