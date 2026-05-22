import { NextResponse } from "next/server";
import { cookies } from "next/headers";
import { env } from "@/lib/env";
import { REFRESH_COOKIE } from "@/lib/auth/cookies";
import { clearSessionCookies } from "@/lib/auth/session";

export async function POST() {
  const refresh = cookies().get(REFRESH_COOKIE)?.value;

  // Best-effort backend revoke. Don't fail the client if it bounces.
  if (refresh) {
    try {
      await fetch(`${env.API_BASE_URL}/api/v1/auth/logout`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refresh_token: refresh }),
        cache: "no-store",
      });
    } catch { /* swallow — client is logging out either way */ }
  }

  clearSessionCookies();
  return new NextResponse(null, { status: 204 });
}
