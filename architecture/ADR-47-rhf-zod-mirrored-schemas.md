# ADR-47: Forms — React Hook Form + Zod mirroring backend constraints

**Status:** Accepted
**Date:** 2026-05-22 (Web Slice 2)
**Tags:** web, forms

## Context
Forms need controlled inputs, client-side validation, and a clean handoff to a mutation hook on submit. Backend already validates everything (Bean Validation on DTOs), but a server round-trip per typo is bad UX. Validation rules need to match the backend exactly — a 12-char password minimum on the server but 8 on the client is the kind of mismatch that surfaces as a confusing 400 to the user.

## Decision
`react-hook-form` for state + submission, `zod` for schema validation, `@hookform/resolvers/zod` to wire them. Zod schemas mirror backend constraints (email format, password ≥12, username regex, file size cap) line-for-line. Schemas live in `lib/auth/schemas.ts`, `lib/upload/schemas.ts`, etc.

## Rationale
- RHF avoids the re-render-everything trap of fully controlled forms — only the changed field re-renders.
- Zod gives schema-first validation that doubles as TypeScript types via `z.infer`.
- Mirroring backend constraints means the client catches everything the server would have, with one source of truth per field (the schema file).

## Consequences
- Backend errors that slip past client validation (e.g., 409 username collision — a uniqueness check the client can't do) are surfaced via `setError("root", { message })`.
- When backend constraints change, the Zod schema must change too — easy to forget. A periodic audit pass against the OpenAPI spec is the planned safeguard.
- Bundle cost: RHF + Zod adds ~30 KB to auth pages. Acceptable for the UX win; documented in §6.8 of `systemdesign.md`.
