import { NextResponse } from "next/server";
import { cookies } from "next/headers";
import { env } from "@/lib/env";
import { REFRESH_COOKIE } from "@/lib/auth/cookies";
import { clearSessionCookies, setSessionCookies, type AuthResponse } from "@/lib/auth/session";

export async function POST() {
  const refresh = cookies().get(REFRESH_COOKIE)?.value;
  if (!refresh) {
    return NextResponse.json({ error: "No refresh token" }, { status: 401 });
  }

  const upstream = await fetch(`${env.API_BASE_URL}/api/v1/auth/refresh`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refresh_token: refresh }),     // backend uses snake_case
    cache: "no-store",
  });

  if (!upstream.ok) {
    // Refresh failed — clear our cookies so the client can re-login cleanly.
    clearSessionCookies();
    return NextResponse.json({ error: "Refresh failed" }, { status: 401 });
  }

  const auth = (await upstream.json()) as AuthResponse;
  setSessionCookies(auth);
  return NextResponse.json({ ok: true });
}
