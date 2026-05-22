"use client";

import { ApiError } from "./client";

/**
 * Same-origin fetch wrapper for authenticated endpoints. Hits Next.js Route
 * Handlers (e.g. `/api/users/me`) which forward to Spring with the cookie-stored
 * Bearer token. Browser sends cookies automatically (same-origin); we don't need
 * to attach them manually.
 *
 * Refresh dance: on a 401, attempts {@code POST /api/auth/refresh} once. If it
 * succeeds, retries the original request once. If it fails, the caller gets the
 * 401 (and the higher-level code should redirect to /login).
 */
export async function authedFetch<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const url = path.startsWith("/") ? path : `/${path}`;

  const doFetch = () =>
    fetch(url, {
      ...init,
      credentials: "same-origin",                   // cookies are same-origin (Next.js)
      headers: {
        Accept: "application/json",
        ...(init.body ? { "Content-Type": "application/json" } : {}),
        ...init.headers,
      },
      cache: init.cache ?? "no-store",
    });

  let res = await doFetch();

  if (res.status === 401 && !path.startsWith("/api/auth/")) {
    const refreshRes = await fetch("/api/auth/refresh", {
      method: "POST",
      credentials: "same-origin",
      cache: "no-store",
    });
    if (refreshRes.ok) {
      res = await doFetch();
    }
  }

  if (!res.ok) {
    let body: unknown = undefined;
    try { body = await res.json(); } catch { /* ignore */ }
    throw new ApiError(res.status, url, body);
  }
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}
