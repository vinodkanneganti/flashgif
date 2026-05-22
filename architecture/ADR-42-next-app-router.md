# ADR-42: Next.js App Router + Server Components

**Status:** Accepted
**Date:** 2026-05-22 (Web Slice 1)
**Tags:** web, rendering

## Context
The web client has three rendering shapes: SEO-critical pages (trending, search, channels) that benefit from server-rendered HTML; auth and dashboard pages where SEO is irrelevant; and Route Handlers that proxy the auth cookie. Pages Router would handle this but doubles down on `getServerSideProps`-style data plumbing.

## Decision
Next.js 14 App Router. Pages default to server components; client components opt in with `"use client"`. SEO pages SSR with streamed HTML; interactive pages are client islands. Same `app/` tree hosts Route Handlers under `app/api/*`.

## Rationale
- Server components stream HTML that already has data — no spinner-then-content for the first paint on `/`, `/search`, `/channels/[username]`.
- SEO crawlers see real content (trending grid, channel bio) without JavaScript execution.
- Route Handlers under `app/api/*` give us the cookie-proxy chain without a parallel API server.
- Server / client component split is enforced by the framework, not convention — fewer ways to leak server-only deps into the bundle.

## Consequences
- The "use client" directive needs careful placement — a misplaced client boundary collapses the whole subtree into the client bundle.
- Server-only utilities (`next/headers`, `cookies()`) are easy to accidentally import from a client file; tsserver catches it but the error message isn't always obvious.
- Mental model: "could a search engine render this usefully" decides where the client boundary lives.
