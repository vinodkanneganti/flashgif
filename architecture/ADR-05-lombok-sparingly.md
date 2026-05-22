# ADR-05: Lombok in, used sparingly

**Status:** Accepted
**Date:** 2026-05-20 (Slice 0)
**Tags:** backend, conventions

## Context
Java + Spring boilerplate (getters, setters, builders, all-args constructors) compounds fast. Lombok eliminates it but has a polarising reputation — opaque magic in the worst cases.

## Decision
Lombok in. Allowed annotations: `@Getter`, `@Setter` (selectively), `@Builder`, `@RequiredArgsConstructor`, `@Slf4j`. Forbidden on entities: `@Data`, `@EqualsAndHashCode`, `@ToString` (they generate behaviour that breaks JPA proxies or leaks lazy fields).

## Rationale
- Constructor injection via `@RequiredArgsConstructor` removes the most repetitive boilerplate in Spring services.
- `@Builder` is the cleanest way to construct test DTOs and complex value objects.
- The forbidden annotations are the ones that historically cause JPA / equality bugs — banning them upfront avoids the litigation later.
- Standard convention in the Spring ecosystem; new contributors already know it.

## Consequences
- Build needs the Lombok annotation processor wired in (`compileOnly` + `annotationProcessor`).
- Generated code isn't visible without an IDE plugin — minor onboarding step.
- Reviewers enforce the "no `@Data` on entities" rule; a future ArchUnit/Checkstyle rule could automate it.
