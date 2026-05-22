/**
 * httpOnly cookie names + default options for the auth pair.
 * Access mirrors the backend's 15-min JWT TTL; refresh mirrors the 30d.
 */

export const ACCESS_COOKIE  = "flashgif_access";
export const REFRESH_COOKIE = "flashgif_refresh";

/** Path scoping for the refresh cookie — only sent to /api/auth/refresh + /logout. */
export const REFRESH_COOKIE_PATH = "/api/auth";

/** Built once per call to satisfy Next.js's cookies() API typing. */
export function accessCookieOptions(maxAgeSeconds: number) {
  return {
    httpOnly: true,
    secure:   process.env.NODE_ENV === "production",
    sameSite: "lax" as const,
    path:     "/",
    maxAge:   maxAgeSeconds,
  };
}

export function refreshCookieOptions(maxAgeSeconds: number) {
  return {
    httpOnly: true,
    secure:   process.env.NODE_ENV === "production",
    sameSite: "lax" as const,
    path:     REFRESH_COOKIE_PATH,
    maxAge:   maxAgeSeconds,
  };
}

/** Used by /logout to clear cookies on the way out. */
export function clearedCookieOptions(path: string) {
  return {
    httpOnly: true,
    secure:   process.env.NODE_ENV === "production",
    sameSite: "lax" as const,
    path,
    maxAge:   0,
  };
}
