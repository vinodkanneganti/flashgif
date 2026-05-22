import "server-only";
import { cookies } from "next/headers";
import {
  ACCESS_COOKIE,
  REFRESH_COOKIE,
  REFRESH_COOKIE_PATH,
  accessCookieOptions,
  clearedCookieOptions,
  refreshCookieOptions,
} from "./cookies";

/** Shape of the backend's AuthResponse — snake_case wire format. */
export type AuthResponse = {
  access_token: string;
  expires_in_seconds: number;
  refresh_token: string;
  token_type: string;
};

const REFRESH_TTL_SECONDS = 60 * 60 * 24 * 30; // 30 days; mirrors backend default

/** Set both auth cookies from a backend AuthResponse. */
export function setSessionCookies(auth: AuthResponse): void {
  const jar = cookies();
  jar.set(ACCESS_COOKIE,  auth.access_token,  accessCookieOptions(auth.expires_in_seconds));
  jar.set(REFRESH_COOKIE, auth.refresh_token, refreshCookieOptions(REFRESH_TTL_SECONDS));
}

/** Clear both auth cookies (used by /logout). */
export function clearSessionCookies(): void {
  const jar = cookies();
  jar.set(ACCESS_COOKIE,  "", clearedCookieOptions("/"));
  jar.set(REFRESH_COOKIE, "", clearedCookieOptions(REFRESH_COOKIE_PATH));
}
