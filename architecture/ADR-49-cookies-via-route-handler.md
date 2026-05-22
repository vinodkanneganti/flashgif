# ADR-49: httpOnly cookies via Next.js Route Handler proxy

**Status:** Accepted
**Date:** 2026-05-22 (Web Slice 2)
**Tags:** web, security, auth

## Context
The web client needs to attach the JWT access token to backend requests. The two storage options are `localStorage` (JavaScript-readable — XSS-exfiltrable) or httpOnly cookies (the browser sends them automatically but JS can't read them). XSS is a real risk in any app with user-generated content; our threat model can't assume "we won't have XSS."

## Decision
Tokens stored only in httpOnly cookies set by Next.js Route Handlers. `flashgif_access` (15-min TTL, `SameSite=Lax`) and `flashgif_refresh` (30-day, `Path=/api/auth`, `SameSite=Lax`). The browser never sees raw token values. Browser-side code calls same-origin Next.js routes; Route Handlers read the cookie and forward to Spring with `Authorization: Bearer`.

## Rationale
- httpOnly is the only XSS-proof token storage. `Secure` + `SameSite=Lax` covers CSRF for our same-origin model.
- Route Handler proxying keeps the architecture clean — no special CORS handling for the auth path, no token-in-URL flows, no JS-readable storage.
- Path-scoping the refresh cookie to `/api/auth/*` minimises its blast radius (it's never sent to non-auth routes).

## Consequences
- Every authed endpoint needs a Route Handler counterpart in `app/api/*`. The `proxyToBackend` helper (ADR-50) cuts boilerplate.
- Public endpoints (search, trending, channels) stay direct to Spring to avoid the same-origin hop.
- One extra hop per authed call (browser → Next → Spring); measured at ~5-10 ms on a warm pod, negligible in our latency budget.
