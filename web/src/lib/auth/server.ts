import "server-only";
import { cookies } from "next/headers";
import { env } from "@/lib/env";
import { ACCESS_COOKIE } from "./cookies";

/**
 * Read the access JWT from the httpOnly cookie. Server components + Route
 * Handlers only. Browser code cannot reach this.
 */
export function readAccessToken(): string | undefined {
  return cookies().get(ACCESS_COOKIE)?.value;
}

export type Me = {
  id: string;
  email: string;
  username: string;
  display_name: string;
  status: string;
  created_at: string;
};

/**
 * SSR helper: fetch the current user using the cookie token. Returns null
 * when unauthenticated or on any error (callers render the logged-out shell).
 *
 * Bypasses the Next.js Route Handler — server-side direct hop to Spring is
 * faster and avoids a same-origin round-trip during SSR.
 */
export async function getCurrentUser(): Promise<Me | null> {
  const token = readAccessToken();
  if (!token) return null;

  try {
    const res = await fetch(`${env.API_BASE_URL}/api/v1/users/me`, {
      headers: { Authorization: `Bearer ${token}` },
      cache:   "no-store",
    });
    if (!res.ok) return null;
    return (await res.json()) as Me;
  } catch {
    return null;
  }
}
