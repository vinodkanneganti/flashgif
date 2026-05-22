# ADR-43: Hand-roll Next.js scaffold (skip `create-next-app`)

**Status:** Accepted
**Date:** 2026-05-22 (Web Slice 1)
**Tags:** web, tooling

## Context
`create-next-app` is interactive, opinionated about ESLint / Prettier configs, ships a sample home page, and tends to include boilerplate (default fonts, example CSS, vestigial assets) we'd immediately remove. For a project with a clear stack already chosen (Tailwind + shadcn + TS strict + pnpm), the generator is friction.

## Decision
Hand-wrote `package.json`, `tsconfig.json`, `next.config.mjs`, `tailwind.config.ts`, `postcss.config.mjs`, `globals.css`, and a minimal `app/layout.tsx` + `app/page.tsx`. Pinned exact versions in `package.json` (no carets).

## Rationale
- Cleaner starting state — no `pages/api/hello.ts`, no logos, no example content to delete.
- Exact dep versions avoid surprise minor bumps; lockfile diffs are caused by intentional upgrades only.
- ~10 minutes to type the configs vs ~15 minutes of generator + cleanup.

## Consequences
- When Next majors are released we update configs by hand rather than re-running the generator. Fine — we're not far enough from the template that the diff is meaningful.
- New contributors don't get the generator's hand-holding; the file layout in `systemdesign.md` §6.2 stands in for it.
- The `pnpm` choice is committed at scaffold time — switching package managers later means rewriting the lockfile.
